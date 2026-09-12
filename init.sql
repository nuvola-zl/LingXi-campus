-- ============================================================
-- 0. 启用扩展
-- ============================================================
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 一、AI 域表（原有 8 张）
-- ============================================================

-- 1. model（AI 模型配置）
CREATE TABLE model (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_recommended BOOLEAN DEFAULT FALSE,
    is_beta BOOLEAN DEFAULT FALSE,
    sort INT DEFAULT 0,
    status BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. "group"（AI 会话分组，注意加引号因为是关键字）
CREATE TABLE "group" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    sort INT DEFAULT 0,
    status BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. chat_session（聊天会话）
CREATE TABLE chat_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    group_id BIGINT,
    type VARCHAR(50),
    title VARCHAR(200),
    status BOOLEAN DEFAULT TRUE,
    is_top BOOLEAN DEFAULT FALSE,
    last_active_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. chat_message（聊天消息）
CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    status BOOLEAN DEFAULT TRUE,
    metadata_json JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_message_session ON chat_message(session_id);

-- 5. attachment（消息附件）
CREATE TABLE attachment (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    file_name VARCHAR(512),
    mime_type VARCHAR(128),
    file_size BIGINT,
    storage_path VARCHAR(2048),
    sha256 VARCHAR(64),
    source_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_attachment_message ON attachment(message_id);

-- 6. kb_library（知识库）
CREATE TABLE kb_library (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    type VARCHAR(16) NOT NULL DEFAULT 'personal',
    owner_id BIGINT NOT NULL,
    is_top BOOLEAN NOT NULL DEFAULT FALSE,
    cover_image VARCHAR(600),
    created_at TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_library_name_owner UNIQUE (name, owner_id)
);

COMMENT ON TABLE kb_library IS '知识库表';
COMMENT ON COLUMN kb_library.type IS '知识库类型: personal/team';
COMMENT ON COLUMN kb_library.owner_id IS '所属用户ID';

-- 7. kb_media（知识库文件）
CREATE TABLE kb_media (
    id BIGSERIAL PRIMARY KEY,
    library_id BIGINT NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(2048) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    total_chunks INT DEFAULT 0,
    parsed_chunks INT DEFAULT 0,
    error_message VARCHAR(512),
    created_at TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_media_sha256_library UNIQUE (sha256, library_id)
);

COMMENT ON TABLE kb_media IS '媒体表-存储知识库内上传的文件元信息';
COMMENT ON COLUMN kb_media.status IS '解析状态: PENDING/PARSING/PARSED/FAILED';

-- 8. kb_chunk（分片向量表）
CREATE TABLE kb_chunk (
    id BIGSERIAL PRIMARY KEY,
    library_id BIGINT NOT NULL,
    media_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    embedding VECTOR(1024),
    chunk_index INT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP(0) NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE kb_chunk IS '分片表-存储解析后的文本分片及向量嵌入';
COMMENT ON COLUMN kb_chunk.embedding IS '向量嵌入(1024维)';

-- 向量索引
CREATE INDEX idx_chunk_embedding ON kb_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_chunk_library_id ON kb_chunk(library_id);
CREATE INDEX idx_chunk_media_id ON kb_chunk(media_id);

-- 全文索引（BM25）
CREATE INDEX idx_chunk_content_fulltext ON kb_chunk USING GIN (to_tsvector('simple', content));

-- 知识库索引
CREATE INDEX idx_library_owner_id ON kb_library(owner_id);
CREATE INDEX idx_library_type ON kb_library(type);
CREATE INDEX idx_library_is_top ON kb_library(is_top);
CREATE INDEX idx_library_created_at ON kb_library(created_at);

-- 媒体文件索引
CREATE INDEX idx_media_library_id ON kb_media(library_id);
CREATE INDEX idx_media_status ON kb_media(status);
CREATE INDEX idx_media_sha256 ON kb_media(sha256);
CREATE INDEX idx_media_created_at ON kb_media(created_at);

-- RRF 评分函数
CREATE OR REPLACE FUNCTION rrf_score(rank bigint, rrf_k int DEFAULT 60)
RETURNS numeric
LANGUAGE SQL IMMUTABLE PARALLEL SAFE
AS $$ SELECT COALESCE(1.0 / ($1 + $2), 0.0); $$;

COMMENT ON FUNCTION rrf_score(bigint, int) IS 'RRF (Reciprocal Rank Fusion) 评分函数';

-- 更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$ BEGIN NEW.updated_at = NOW(); RETURN NEW; END; $$ language 'plpgsql';

-- 触发器
CREATE TRIGGER update_kb_library_updated_at
    BEFORE UPDATE ON kb_library FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_kb_media_updated_at
    BEFORE UPDATE ON kb_media FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- 二、系统域表（新建 4 张）
-- ============================================================

-- 9. sys_dept（部门）
CREATE TABLE sys_dept (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    sort_order INT DEFAULT 0,
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10. sys_user（用户）
CREATE TABLE sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100),
    real_name VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    avatar VARCHAR(255),
    dept_id BIGINT REFERENCES sys_dept(id),
    role_type VARCHAR(20) DEFAULT 'EMPLOYEE',
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN sys_user.role_type IS '角色类型：EMPLOYEE员工 ENGINEER工程师 ADMIN管理员';

-- 11. sys_role（角色，可选）
CREATE TABLE sys_role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(200),
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 12. sys_user_role（用户角色关联，可选）
CREATE TABLE sys_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    UNIQUE(user_id, role_id)
);


-- ============================================================
-- 三、业务域表（新建 6 张）
-- ============================================================

-- 13. biz_engineer_group（工程师组）
CREATE TABLE biz_engineer_group (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    category_codes JSONB DEFAULT '[]',
    leader_id BIGINT REFERENCES sys_user(id),
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN biz_engineer_group.category_codes IS '负责分类编码 JSON 数组';

-- 14. biz_engineer_group_member（工程师组成员）
CREATE TABLE biz_engineer_group_member (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES biz_engineer_group(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    UNIQUE(group_id, user_id)
);

-- 15. biz_ticket_category（工单分类）
CREATE TABLE biz_ticket_category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    parent_id BIGINT DEFAULT 0,
    default_group_id BIGINT REFERENCES biz_engineer_group(id),
    sla_response_minutes INT DEFAULT 30,
    sla_resolve_minutes INT DEFAULT 240,
    sort_order INT DEFAULT 0,
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN biz_ticket_category.sla_response_minutes IS 'SLA响应时效（分钟）';
COMMENT ON COLUMN biz_ticket_category.sla_resolve_minutes IS 'SLA解决时效（分钟）';

-- 16. biz_service_catalog（服务目录，AI 意图识别用）
CREATE TABLE biz_service_catalog (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category_id BIGINT REFERENCES biz_ticket_category(id),
    trigger_keywords JSONB DEFAULT '[]',
    description TEXT,
    default_priority INT DEFAULT 2,
    default_group_id BIGINT REFERENCES biz_engineer_group(id),
    faq_doc_id BIGINT,
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN biz_service_catalog.trigger_keywords IS 'AI意图识别触发关键词 JSON 数组';
COMMENT ON COLUMN biz_service_catalog.faq_doc_id IS '关联知识库文档ID';

-- 17. biz_ticket（工单核心表）
CREATE TABLE biz_ticket (
    id BIGSERIAL PRIMARY KEY,
    ticket_no VARCHAR(50) UNIQUE NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category_id BIGINT REFERENCES biz_ticket_category(id),
    priority INT DEFAULT 2,
    status INT DEFAULT 1,
    source VARCHAR(20) DEFAULT 'AI',
    requester_id BIGINT NOT NULL REFERENCES sys_user(id),
    assignee_id BIGINT REFERENCES sys_user(id),
    group_id BIGINT REFERENCES biz_engineer_group(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,
    satisfaction INT,
    feedback TEXT,
    ai_session_id VARCHAR(64)
);

COMMENT ON COLUMN biz_ticket.status IS '1待处理 2处理中 3待确认 4已关闭';
COMMENT ON COLUMN biz_ticket.source IS 'AI自动创建 / WEB人工创建 / ADMIN后台创建';
COMMENT ON COLUMN biz_ticket.priority IS '1紧急 2普通 3低';
COMMENT ON COLUMN biz_ticket.ai_session_id IS '关联AI对话会话ID';

CREATE INDEX idx_ticket_requester ON biz_ticket(requester_id);
CREATE INDEX idx_ticket_assignee ON biz_ticket(assignee_id);
CREATE INDEX idx_ticket_status ON biz_ticket(status);
CREATE INDEX idx_ticket_created ON biz_ticket(created_at DESC);
CREATE INDEX idx_ticket_no ON biz_ticket(ticket_no);

-- 18. biz_ticket_comment（工单处理记录/时间线）
CREATE TABLE biz_ticket_comment (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES biz_ticket(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    content TEXT NOT NULL,
    type VARCHAR(20) DEFAULT 'COMMENT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN biz_ticket_comment.type IS 'COMMENT评论 SYSTEM系统记录 HANDOVER转交';

CREATE INDEX idx_comment_ticket ON biz_ticket_comment(ticket_id);


-- ============================================================
-- 四、初始化数据
-- ============================================================

-- 部门
INSERT INTO sys_dept (name, parent_id, sort_order) VALUES
('总部', 0, 1),
('技术部', 1, 1),
('人力资源部', 1, 2),
('行政部', 1, 3),
('财务部', 1, 4);

-- 工程师组
INSERT INTO biz_engineer_group (name, category_codes) VALUES
('IT 运维组', '["it"]'),
('HR 服务组', '["hr"]'),
('行政服务组', '["admin"]'),
('财务支持组', '["finance"]');

-- 工单分类
INSERT INTO biz_ticket_category (name, code, default_group_id, sla_response_minutes, sla_resolve_minutes) VALUES
('IT 运维', 'it', 1, 30, 240),
('人力资源', 'hr', 2, 60, 480),
('行政服务', 'admin', 3, 120, 720),
('财务服务', 'finance', 4, 240, 1440);

-- 服务目录（AI 意图识别用）
INSERT INTO biz_service_catalog (name, category_id, trigger_keywords, description, default_priority, default_group_id) VALUES
('邮箱问题', 1, '["邮箱","邮件","Outlook","登不上","发不了邮件"]', '企业邮箱登录、配置、故障排查', 2, 1),
('VPN 申请', 1, '["VPN","远程","访问内网","连不上VPN"]', 'VPN 账号申请与配置', 2, 1),
('账号权限', 1, '["账号","权限","系统登录","忘记密码"]', '系统账号申请与权限开通', 2, 1),
('请假申请', 2, '["请假","年假","病假","调休","还剩几天假"]', '请假流程与余额查询', 2, 2),
('会议室预定', 3, '["会议室","预定","开会","哪里有会议室"]', '会议室查询与预定', 3, 3),
('报销咨询', 4, '["报销","发票","差旅","怎么报销"]', '报销政策与流程', 2, 4),
('工资查询', 2, '["工资","薪资","发工资","工资条"]', '工资查询与疑问', 2, 2);

-- 初始用户（密码先用明文 123456，后面你自己改）
INSERT INTO sys_user (username, real_name, email, dept_id, role_type, status) VALUES
('admin', '系统管理员', 'admin@company.com', 1, 'ADMIN', 1),
('engineer1', 'IT工程师小李', 'it1@company.com', 2, 'ENGINEER', 1),
('engineer2', 'HR专员小张', 'hr1@company.com', 3, 'ENGINEER', 1),
('employee1', '普通员工小王', 'emp1@company.com', 2, 'EMPLOYEE', 1);