# LingXi-campus（灵犀校园）

> 设计思路、问题排查与测试验证详见 [docs/](./docs/)

一个全栈式校园 AI 助手，基于多模块架构，支持多模态对话、知识库问答（RAG）、IT 智能工单 Agent，以及会议室、设备等校园行政场景的智能办理。

## 功能特性

### 核心对话

- **多模态 AI 对话** - 支持文本、图片等多模态交互
- **SSE 流式响应** - 实时流式返回 AI 回复（含思考过程）
- **会话管理** - 会话分组、置顶、软删除
- **自动标题生成** - 基于首轮对话自动生成会话标题
- **模型切换** - 支持多种 AI 模型切换

### IT Agent 智能工单

- **意图识别 + 状态机编排** - AgentOrchestrator 统一调度，诊断 / 收集 / 确认多状态流转
- **工具调用** - TicketQueryTool / TicketCloseTool / TicketUrgentTool / KnowledgeSearchTool
- **数据确认机制** - ConfirmDataPreparer 在关键操作前与用户确认

### RAG 知识库（Astra）

- **多格式文档解析** - PDF（PDFBox）、Word（POI）、Markdown、TXT 分格式提取器
- **向量检索** - pgvector 相似度检索 + Hyde 假设性文档检索 + Rerank 重排序
- **异步解析** - Redis Stream 消息队列削峰填谷，SSE 实时推送解析进度

### 校园行政场景

- **HR 会议室** - 会议室查询与预订（MeetingRoomTools）
- **设备管理** - 设备申请、审批、超时补偿任务（DeviceTools + 定时任务）
- **统一工具网关** - ToolCallGateway 统一注册和管理各类场景工具

## 技术架构

- **后端**: Spring Boot 3.x + Spring AI Alibaba（多模块 Maven 工程）
- **前端**: Vue 3 + TypeScript + Vite
- **数据库**: PostgreSQL + pgvector
- **AI 集成**: 阿里云 DashScope（通义千问、text-embedding-v3）
- **缓存/队列**: Redis + Redis Stream

## 项目结构

```
LingXi-campus/
├── backend/                          # 后端服务（多模块）
│   ├── ai-common/                    # 公共模块（常量、枚举、工具类）
│   ├── ai-domain/                    # 领域层（实体、DTO、VO + MyBatis Mapper）
│   │   └── src/main/resources/mapper/
│   │       ├── ChatMessageMapper.xml
│   │       ├── ChatSessionMapper.xml
│   │       ├── KbChunkMapper.xml
│   │       ├── KbLibraryMapper.xml
│   │       └── KbMediaMapper.xml
│   ├── ai-infrastructure/            # 基础设施（缓存、配置属性）
│   └── ai-server/                    # 主服务模块
│       └── src/main/java/top/lingxi/campus/
│           ├── ai/                   # AI 基础能力（标题生成等）
│           ├── chat/                 # 对话与会话管理
│           ├── itAgent/              # IT 智能工单 Agent
│           │   ├── agent/            # 编排核心（状态机、工具、服务）
│           │   ├── pipeline/         # 对话流水线
│           │   └── ticket/           # 工单查询服务
│           ├── rag/                  # 知识库 RAG
│           │   ├── ingestion/        # 文档摄入（extractor 分格式提取器）
│           │   ├── parse/            # 智能分块、文件解析
│           │   ├── file/             # Hyde 检索等文件服务
│           │   └── service/          # 向量化检索、Rerank
│           ├── admin/                # 行政后台（审批任务、设备、采购）
│           ├── hr/                   # HR 场景（会议室）
│           ├── tool/                 # 工具网关与各场景工具
│           └── config/               # ChatClient、异步线程池等配置
├── frontend/                         # 前端应用 (Vue 3 + TypeScript)
└── README.md
```

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- PostgreSQL 12+（需启用 pgvector 扩展）
- Redis 6+

### 后端启动

```bash
cd backend/

# 构建
mvn clean package -DskipTests

# 运行主服务
mvn spring-boot:run -pl ai-server

# 或直接运行 JAR
java -jar ai-server/target/ai-server-*.jar
```

### 前端启动

```bash
cd frontend/

npm install
npm run dev        # 开发模式
npm run build      # 生产构建
```

### 数据库配置

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE DATABASE "lingxi-campus";
```

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/lingxi-campus
    username: postgres
    password: your_password
```

> AI 相关的 API Key（DashScope 等）在 `application.yaml` 中配置。

## 核心设计

### IT Agent 工单流程

```
用户消息 → ChatPipeline → AgentOrchestrator（意图决策）
    → DiagnosingState（知识库诊断）→ CollectingState（信息收集）
    → ConfirmingState（ConfirmDataPreparer 确认数据）
    → 工具执行（建单 / 查询 / 加急 / 关单）
```

### RAG 知识库流程

```
文件上传 → Media 元信息 → Redis Stream 队列 → 解析消费
    → 分格式 Extractor 提取 → 智能分块 → Embedding 向量化
    → pgvector 存储 → 查询时：Hyde 扩展 + 向量检索 + Rerank → LLM 生成
```

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 3.x, Spring AI Alibaba |
| 前端框架 | Vue 3, TypeScript, Vite |
| 数据库 | PostgreSQL, pgvector |
| 缓存/队列 | Redis, Redis Stream |
| AI 模型 | 通义千问, text-embedding-v3 |
| 文档解析 | Apache PDFBox, Apache POI |
| ORM | MyBatis / MyBatis-Plus |
| 构建工具 | Maven, npm |

## License

MIT
