# LingXi Campus Backend · 灵犀校园服务端

基于 Spring Boot + Spring AI 的**校园智能服务平台**后端。覆盖 **智能报修（Agent）、教务服务、后勤服务、知识库问答** 四大场景，让 AI 不仅能回答问题，更能动手办事。

## 架构总览

```javascript
┌────────────────────────── 接入层 ──────────────────────────┐
│  ChatController(文件处理/路由)   AstraController(SSE 问答)   │
└────────────────────────────┬───────────────────────────────┘
                             ▼
┌────────────────────────── 对话层 ──────────────────────────┐
│  ChatServiceImpl(domain 路由/跨域转接)  ChatPipeline(会话/消息)│
│  意图识别: 关键词(数据库) → LLM 策略  (Strategy 模式)        │
└───────┬──────────────────────┬───────────────┬─────────────┘
        ▼                      ▼               ▼
┌──────────────┐   ┌────────────────────┐   ┌──────────────────┐
│  报修 Agent   │   │  教务 / 后勤         │   │  RAG 知识库       │
│ 状态机+ReAct │   │  Function Calling  │   │  检索/问答        │
└──────┬───────┘   └─────────┬──────────┘   └────────┬─────────┘
       │                     │                       │
       ▼                     ▼                       ▼
┌────────────────────────────────────────────────────────────┐
│ 执行层: ToolRegistry(Agent) / ToolCallGateway(统一治理)     │
│ 持久层: PostgreSQL+pgvector · Redis(CAS状态/Stream/DLQ)     │
└────────────────────────────────────────────────────────────┘
```

## 技术栈

| 层级 | 选型 |
| --- | --- |
| 框架 | Spring Boot 3.x · Spring AI 1.x · Reactor |
| 大模型 | 阿里云 DashScope（qwen-flash / qwen3-rerank / text-embedding-v3） |
| 数据 | PostgreSQL 16 + pgvector(HNSW) · MyBatis-Plus |
| 中间件 | Redis（Stream 队列 + 消费者组 + Lua CAS）· Redisson（分布式锁） |
| 存储 | 阿里云 OSS |

## 核心功能

### 1. 知识库 RAG（rag）

- **离线入库流水线**（`IngestionPipeline`）：上传 → 提取（策略模式，PDF 按页保留页码）→ 智能切分（段落感知/句子边界/20% 重叠，配置驱动）→ embedding → 落库；Redis Stream 异步消费，重试退避 + 死信队列，批次级进度 SSE 推送
- **在线问答**：Query 改写（失败回退原 query）→ 向量+BM25 双路召回 → **单条 SQL 完成 RRF 融合**（PostgreSQL 下推）→ 相邻 chunk 上下文扩展 → qwen3-rerank 精排 → Prompt 组装 → 流式生成，complete 事件携带 **citations 引用溯源**
- 分片级 `content_hash` 落库，为增量更新预留

### 2. 智能报修 Agent（ai.agent）

- **状态机承载流程**：`DIAGNOSING → COLLECTING → CONFIRMING → COMPLETED`，流程确定性由状态机保证
- **Orchestrator 决策层**：轻量模型输出结构化 JSON 决策（RESPOND/TRANSITION/TOOL_CALL/COMPLETE），决策 reasoning 全量留痕
- **诊断态内嵌 ReAct 闭环**：`search_knowledge` 检索 → Observation 回填 → 再决策，**循环上限熔断**
- **检索质量门禁**：向量/BM25 阈值过滤，`KNOWLEDGE_EMPTY` 协议标记驱动确定性转单（模糊性归 LLM、确定性归代码）
- **工程保障**：Redis Lua CAS 状态乐观锁、Redisson 锁防重复建单、标题/工单 24h 查重（精确+包含匹配）、紧急度关键词规则、单号 Redis 原子序列

### 3. 教务 / 后勤（Function Calling）

- Spring AI tool-calling 框架内循环，支持**单轮多工具链式调用**
- **身份安全**：userId 经 `ToolContext` 从调用链注入（模型不可见、不可篡改），兜底 ThreadLocal；防御 prompt 注入越权
- **统一治理入口 `ToolCallGateway`**：审计日志 + 耗时 + 异常兜底 + 超时保护
- 教务：请销假（日期重叠查重 / AI 自动审批规则 / 审批留痕）、课堂补签（规则引擎按次数分级）
- 后勤：场地预约、器材借用（悲观锁防超卖、有库存同步分配 / 无库存转采购、到货自动分配）、补偿与超时回收任务

## 工程亮点

- **SSE 事件对象建模**：`AstraChatEvent` 结构化事件，序列化收敛单一出口，传输协议与业务模型解耦
- **入库流水线模式**：4 个 Parser 的重复样板收敛为唯一编排者 + 薄提取器策略
- **配置收口**：切分/检索/rerank/上传白名单等全部 `AstraProperties` 类型化，无散落硬编码
- **安全**：JWT 认证 + `@RequireRole` 注解鉴权；工具参数身份与登录态绑定
- **可演进**：chunk content_hash（增量更新地基）、检索评测留口、Gateway 限流熔断扩展点

## 快速启动

```bash
# 依赖：JDK 17+ · PostgreSQL(pgvector) · Redis
# 1. 建库建表（项目根目录 init.sql），执行 alter_chunk_content_hash.sql

# 2. 配置 application-local.yml（DashScope key / DB / Redis / OSS）
#    参考 application.yml 占位符

# 3. 启动（IDEA Program arguments: --spring.profiles.active=local）
./mvnw spring-boot:run
```

## 项目结构

```javascript
top.lingxi.campus
├── rag/           # 知识库：controller / service / ingestion(入库流水线) / vector
├── ai/
│   ├── agent/     # 报修 Agent：orchestration(决策/状态机) / dialog(三态) / tool / retrieval(HyDE)
│   └── service/   # 对话编排、意图识别、会话消息
├── chat/          # 管道(pipeline) / 意图 Handler 链
├── tool/          # ToolCallGateway + 三域工具（教务/后勤）
├── domain/        # 实体 / Mapper / DTO
└── infra/         # 配置 / OSS / Redis Stream
```

## 说明

- 文档对话、反馈知识库沉淀、文生图等模块为开发中/已下线能力，详见提交历史
- 本地开发配置（含敏感信息）经 `application-local.yml` 提供，仓库内零密钥