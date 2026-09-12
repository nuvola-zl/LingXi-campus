# HazeAI-Hub IT 运维 Agent

基于 ReAct 范式的 IT 运维智能工单助手。

## 架构亮点

- **自研 Agent 状态机引擎**：事件驱动，支持 Tool-Calling 自循环与 Redis 状态持久化
- **ReAct 决策**：LLM 通过 Thought-Action-Observation 自主决策，替代硬编码规则链
- **多路 RAG 检索**：BM25 + HNSW 向量检索 + RRF 融合 + Qwen ReRank + QueryRewrite + **HyDE**
- **三级意图识别**：LLM(3s超时) → 关键词降级 → 服务目录降级，策略模式实现
- **生产级保障**：Redisson 幂等锁 / 对话记忆摘要 / 状态过期防护 / SSE 流式响应

## 技术栈

Spring Boot 3.x | Reactor | Redis | PostgreSQL + PGVector | DashScope(qwen) | Redisson

## 快速启动

```bash
# 1. 启动 Redis & PostgreSQL
docker-compose up -d

# 2. 运行
./mvnw spring-boot:run

# Haze AI Hub — 企业级智能 AI 助手平台

&lt;p align="center"&gt;
  &lt;img src="https://img.shields.io/badge/Spring%20Boot-3.x-green" alt="Spring Boot"&gt;
  &lt;img src="https://img.shields.io/badge/Spring%20AI-1.x-blue" alt="Spring AI"&gt;
  &lt;img src="https://img.shields.io/badge/DashScope-通义千问-orange" alt="DashScope"&gt;
  &lt;img src="https://img.shields.io/badge/PostgreSQL-pgvector-336791" alt="PostgreSQL"&gt;
  &lt;img src="https://img.shields.io/badge/Redis-Stream-red" alt="Redis"&gt;
&lt;/p&gt;

## 📌 项目简介

**Haze AI Hub** 是一款面向企业内部的智能化 AI 助手平台，基于 **Spring Boot + Spring AI** 构建，深度集成阿里云通义千问大模型。平台覆盖 **IT 运维、人力资源、行政服务** 三大核心领域，提供智能对话、知识库问答、工单自动创建、会议室预定、设备申领等一站式企业服务能力。

&gt; **核心设计理念**：通过 Agent 状态机 + RAG 知识检索 + 工具调用（Function Calling），让 AI 不仅能"回答问题"，更能"动手办事"。

---

## 🏗️ 技术架构

### 技术栈

| 层级 | 技术选型 |
|------|---------|
| **基础框架** | Spring Boot 3.x, Spring AI 1.x |
| **大模型** | 阿里云 DashScope（deepseek-v3 / qwen-flash / qwen-turbo / text-embedding-v3） |
| **数据层** | PostgreSQL 16 + pgvector（向量扩展）, MyBatis-Plus, Redis 7 |
| **中间件** | Redisson（分布式锁 / Redis Stream）, Caffeine（本地缓存） |
| **存储** | 阿里云 OSS |
| **文档** | Knife4j + SpringDoc OpenAPI |
| **安全** | JWT Token 认证 |

### 架构亮点

- **Handler 链式处理**：按领域（IT/HR/ADMIN）分组，优先级匹配，第一个命中的 Handler 执行
- **Pipeline 管道模式**：统一处理会话创建、消息保存、标题生成等前后置逻辑
- **Agent 状态机**：ReAct 模式（DIAGNOSING → COLLECTING → CONFIRMING → COMPLETED），支持多轮对话建单
- **Orchestrator 决策器**：LLM 动态决策用户意图，替代硬编码 if-else
- **DAG 工作流引擎**：设备申领全流程自动化编排（库存检查 → 分支路由 → 分配/采购 → 完成）
- **混合检索 RAG**：BM25 全文检索 + 向量相似度 + RRF 融合 + ReRank 重排序

---

## ✨ 核心功能

### 1. 🤖 智能对话（AI Chat）
- **多领域支持**：IT 运维 / 人力资源 / 行政服务
- **意图识别**：自动识别生图、查工单、建工单、知识库问答、普通对话等意图
- **流式 SSE 输出**：实时返回 AI 回复，支持思考过程展示（`&lt;think/&gt;` 标签）
- **文件上传对话**：支持 PDF/DOCX/TXT/MD 文件直接上传对话（小文件直接注入，大文件提示走知识库）

### 2. 📚 Astra 知识库
- **多格式解析**：PDF（PDFBox）、Word（POI）、Markdown、TXT
- **智能分块**：基于 Token 估算的智能 Chunking，支持代码/表格特殊处理，重叠率 20%
- **混合检索**：
  - BM25 文本匹配 + pgvector HNSW 向量检索
  - RRF（Reciprocal Rank Fusion）融合排序
  - qwen3-rerank 重排序模型精排
- **HyDE 检索**：短 Query 自动生成假设文档，提升检索召回率
- **异步解析**：Redis Stream 队列 + 消费者组，支持进度推送（SSE）、死信队列（DLQ）、指数退避重试

### 3. 🎫 Agent 工单系统
- **ReAct 状态机**：
  - `DIAGNOSING`：检索知识库 → 给出建议 → 判断是否解决
  - `COLLECTING`：收集问题描述 → 智能提取标题
  - `CONFIRMING`：确认工单信息 → 防重锁创建
  - `COMPLETED`：结束对话
- **防重复机制**：Redis 分布式锁 + 单号查重
- **状态持久化**：Redis CAS（Lua 脚本）保证并发安全
- **对话记忆**：6 轮后自动摘要，跨会话继承上下文

### 4. 👤 HR 智能服务
- 请假申请（年假/病假/事假）+ 记录查询 + 取消
- 忘打卡/迟到补卡申请 + 记录查询
- 重复申请拦截（时间段重叠检测）

### 5. 🏢 行政智能服务
- **会议室管理**：空闲查询、预定、取消、记录查询
- **设备申领**：DAG 自动化流程
  - 有库存：自动分配 → 安装配置 → 通知领取（1-3 分钟）
  - 无库存：自动采购 → 审批 → 到货 → 自动处理（3-5 工作日）
- **设备归还**：提交归还申请 → 管理员确认入库
- **超时回收**：定时任务自动回收 7 天未领取设备
- **死锁恢复**：定时扫描"已到货但未完成"单子，自动恢复 DAG

### 6. 🔧 工程师工单处理
- 待处理工单列表（组内未分配 + 已分配给自己）
- 接单、添加处理记录、标记解决
- 工单关闭后自动沉淀到反馈知识库（Feedback Library）

### 7. 🔄 工单知识沉淀
- 工单关闭后自动触发知识提取
- LLM 总结解决方案（质量校验：步骤数 + 字数阈值）
- 反馈库隔离：与官方 SOP 知识库物理隔离，初始信任分 30
- 向量查重 + LLM 比对去重（DIFFERENT_SOLUTION / BETTER_VERSION / SAME）

---

## 📁 项目结构
