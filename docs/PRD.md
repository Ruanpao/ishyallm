# PRD: 智能医疗知识库问答系统 (IshyaLLM)

## Problem Statement

医生在日常诊疗和科研工作中需要快速查阅大量医学文献、诊疗指南和教材，但传统检索方式效率低下。通用 LLM（如直接使用 DeepSeek）在医疗垂直领域存在两个核心痛点：一是**幻觉率高**（直接问答时约 35%），模型可能编造诊疗标准、药物剂量等关键信息；二是**知识滞后**，模型训练数据无法覆盖最新的诊疗指南版本。

现有解决方案的问题：纯关键词搜索（如 PubMed）无法理解语义；纯 LLM 问答无法溯源到原文，医生不敢采信；商业医疗问答产品往往不透明，无法对接医院自有知识库。

## Solution

构建一个基于 RAG 架构的智能医疗知识库问答系统，允许医生上传科室相关的 PDF 医学文档（指南、教材、论文等），系统自动解析、分块、向量化，当医生提问时从知识库中精准召回相关片段，由 LLM 基于召回结果生成带原文溯源的回答。

核心差异化：
- **双路召回 + RRF 重排**：语义（PGVector）和关键词（ES BM25）互补检索，医学专有名词召回精度更高
- **可溯源**：每一条回答都携带来源页码，医生可点击核验原文
- **科室级数据隔离**：文档按科室归属，检索自动过滤，防止跨科室数据串扰
- **异步摄入管线**：PDF 上传 → Kafka 异步解析/向量化，不阻塞医生操作
- **大小模型路由**：简单事实查询走 Flash 降本提速，复杂诊疗推理走 Pro 保障质量

## User Stories

1. 作为医生，我想上传 PDF 文档（指南/教材/论文），以便系统能基于我的科室知识库回答问题
2. 作为医生，我想在上传时选定文档所属科室，以便文档能被正确的科室成员检索到
3. 作为医生，我想用自然语言提问医疗相关问题，以便快速获取知识库中的答案
4. 作为医生，我想看到回答附带了原文的引用页码，以便核验答案的准确性
5. 作为医生，我想在多轮对话中连续追问，以便深入探讨诊疗方案而不必每次都重复上下文
6. 作为医生，我想查看我的历史对话记录，以便回顾之前的诊疗咨询
7. 作为医生，我想在回答中看到引用的原文片段预览，以便快速判断答案是否相关
8. 作为管理员，我想管理医生账号（增删改查），以便控制系统的访问权限
9. 作为管理员，我想维护模型路由的关键词规则，以便优化问答的模型分配
10. 作为管理员，我想监控文档的解析状态（成功/失败），以便及时发现并处理异常
11. 作为管理员，我想查看系统的运行状态（Kafka积压、ES/PG连接），以便进行运维管理
12. 作为医生，我希望简单的常识性问题（如"高血压的定义"）能够秒级响应，不必每次等待大模型推理
13. 作为医生，我希望系统能告诉我当前回答依据了哪些文档，以便评估信息的时效性和权威性

## Implementation Decisions

### 包结构与模块职责

系统采用单一 Maven 项目结构，构建为可独立运行的 JAR 包，部署 2~3 实例 + Nginx 负载均衡。后端代码按功能分包：

- **gateway**：Spring WebFlux 路由层，对外暴露 REST API + SSE 流式端点。负责请求鉴权（JWT 拦截器）、模型路由分发、首字时间优化（<300ms）
- **ingestion**：文档摄入管线。PDF 上传接收 → PDFBox 解析文本 → 混合递归分块（章节优先，512 tokens） → Kafka 生产者发送到 `pdf-parse-done` topic → 消费者消费并调用千问 Embedding API → 结果写入 `embed-done` topic → 最终消费者写入 PGVector + ES。解析失败的消息进入 `ingestion-dlq` DLQ topic
- **retrieval**：双路召回引擎。从 PGVector（HNSW + 余弦距离）和 ES（IK 分词器 + BM25）并行检索各取 Top-20，RRF 合并去重后取 Top-8。支持科室 Metadata 过滤
- **rag**：RAG 编排核心。Query Rewrite（多轮对话上下文合并） → 调用 retrieval 获取 Top-8 Chunks → 组装 Prompt → 调用 DeepSeek API（流式） → 归因标注（标注每段回答对应的来源页码和文档ID）
- **security**：认证与权限。自建医生/管理员账号体系（BCrypt 密码 + JWT Token）、科室级数据隔离过滤器（写入/检索时强制绑定并过滤科室标识）
- **common**：共享模型（DTO、实体类）、自定义线程池配置（CPU 池 core=2/max=2，IO 池 core=4/max=8）、Kafka Topic 常量、异常定义

### 核心数据流

**文档摄入流**：
```
医生上传 PDF → Gateway → Ingestion(PDFBox解析 → 递归分块)
  → Kafka Topic: pdf-parse-done (批量: 整份文档的所有Chunks)
    → Consumer(cpuPool) → 千问Embedding API
      → Kafka Topic: embed-done
        → Consumer(ioPool) → 写入 PGVector + ES
```

**问答流**：
```
用户提问 → Gateway(JWT校验)
  → RAG(Query Rewrite: 合并历史上下文生成独立问题)
    → Retrieval(并行检索: PGVector Top-20 + ES Top-20)
      → RRF合并取Top-8 → RAG(组装Context+Prompt)
        → 模型路由判定(Flash/Pro)
          → DeepSeek API(streaming) → SSE推送到前端
```

### Kafka 消息体设计

**Topic: pdf-parse-done**
```json
{
  "docId": "DOC-20241201-001",
  "title": "儿童急性淋巴细胞白血病诊疗指南(2024)",
  "version": "2024",
  "department": "儿科",
  "uploadedBy": "doctor-abc",
  "status": "PARSED",
  "chunks": [
    {
      "chunkId": "chunk-uuid",
      "content": "原文文本...",
      "pageNumber": 42,
      "chapter": "治疗/化疗方案",
      "seqOrder": 1
    }
  ]
}
```

**Topic: embed-done**
```json
{
  "chunkId": "chunk-uuid",
  "docId": "DOC-20241201-001",
  "content": "原文文本...",
  "embedding": [0.123, -0.456, ...],
  "pageNumber": 42,
  "department": "儿科"
}
```

### 模型路由规则

基于关键词 + 问题长度的分类器，运行在 Gateway 层：

| 条件 | 路由目标 | 说明 |
|------|---------|------|
| 问题 ≤ 10 字 | DeepSeek Flash | 短问题通常为简单事实查询 |
| 问题 > 20 字 | DeepSeek Pro | 长问题通常需要复杂推理 |
| 11~20 字 + 含 "定义"/"症状"/"正常值" 等关键词 | DeepSeek Flash | 事实型查询 |
| 11~20 字 + 含 "鉴别诊断"/"治疗方案"/"预后" 等关键词 | DeepSeek Pro | 推理型查询 |
| 11~20 字 + 无明确关键词 | DeepSeek Pro（保守默认） | 无法判定时走大模型 |

路由关键词列表维护在外部配置文件中，管理员可运行时修改。

### API 端点设计

| 方法 | 路径 | 说明 | 模块 |
|------|------|------|------|
| POST | `/api/auth/login` | 登录，返回 JWT | security |
| POST | `/api/auth/register` | 注册（仅管理员） | security |
| GET | `/api/chat/history` | 获取历史对话列表 | rag |
| GET | `/api/chat/history/{id}` | 获取单条对话详情 | rag |
| POST | `/api/chat/stream` | 提问，SSE 流式返回 | gateway → rag |
| POST | `/api/documents/upload` | 上传 PDF（含科室字段） | ingestion |
| GET | `/api/documents` | 文档列表（按科室/标题筛选） | ingestion |
| GET | `/api/documents/{id}/status` | 文档解析状态查询 | ingestion |
| POST | `/api/admin/users` | 创建医生账号（管理员） | security |
| GET | `/api/admin/users` | 用户列表（管理员） | security |
| PUT | `/api/admin/routing-rules` | 更新路由关键词（管理员） | gateway |
| GET | `/api/admin/stats` | 系统状态监控（管理员） | common |

### 线程模型

```
请求入口: Netty EventLoop (非阻塞)
  → Gateway 层: 无阻塞操作，在 EventLoop 内完成
    → RAG 编排: 切换到 IO 线程池 (core=4, max=8)
      → Retrieval (ES/PG 查询): IO 线程池
      → DeepSeek API 调用: IO 线程池 (WebClient reactive)
  → SSE 响应: EventLoop 推流

PDF 解析: CPU 线程池 (core=2, max=2) — 独立于请求链路
Kafka 消费者: 2 个消费者线程，各消费一个 partition
```

### 技术依赖

- Spring Boot 4.0 + spring-boot-starter-webflux
- Langchain4j (spring-boot-starter + deepseek4j + dashscope-embedding adapters)
- PDFBox (PDF 解析)
- spring-boot-starter-data-r2dbc + r2dbc-postgresql + pgvector (向量存储)
- Elasticsearch Java Client 8.x + IK 分词器
- Kafka Client 3.x
- java-jwt (JWT 签发与验证)
- Testcontainers (集成测试 ES / PG)

### 关键非功能性约束

- **首字响应时间**：SSE 场景下 <300ms（从请求到达 gateway 到第一个 token 推送到前端）
- **幻觉率**：在人工标注测试集上 <3%（基线：无 RAG 时 ~35%）
- **单实例并发**：IO 线程池 max=8，Kafka 消费者=2，CPU 池=2
- **部署提醒**：Nginx 必须配置 ip_hash 或 sticky session 策略，确保 SSE 长连接始终路由到同一实例

## Testing Decisions

一个好的测试只验证外部行为而非实现细节。对于本项目：

- **ingestion 包**：用 Testcontainers 启动 Kafka，验证 PDF 上传 → 解析 → 分块 → 消息投递到 `pdf-parse-done` 的全流程。PDF 解析支持用已知内容的测试 PDF 验证分块结果
- **retrieval 包**：用 Testcontainers 启动 ES 和 PG（含 pgvector 插件），写入已知测试数据，验证双路召回 + RRF 排序结果是否符合预期。对医学专有名词（如"甲氨蝶呤"）验证 BM25 召回是否优于纯语义召回
- **security 包**：JWT 签发/验证、科室过滤器的单元测试
- **rag 包**：Mock LLM API 与检索层，验证 Query Rewrite 是否正确合并多轮上下文，验证 Prompt 组装是否包含引用信息
- **gateway 包**：集成测试验证 SSE 流式端点的正确性，模型路由的逻辑测试

## Out of Scope

- 前端实现（前后端分离，前端在独立会话中开发）
- 移动端适配
- PDF 中图像/公式的 OCR 识别（仅提取文本层）
- 多语言支持
- 高可用部署（单机 Docker 部署，不包含 K8s 编排）
- LLM 微调（纯 RAG 路线，不涉及模型训练）

## Further Notes

- 科室隔离的实现方案：文档表中存储 `department` 字段，PGVector 的 Metadata 预过滤 和 ES 的 `department` 关键字过滤分别在检索时生效，从存储层做物理拦截
- 去重策略：上传时根据标题+版本号查询 PG 库，已存在的文档返回提示而非创建重复
- 对话历史存储在 PG 中（JSON 格式存储消息列表），按医生 ID 查询
- 千问 Embedding API 支持批量传入文本，因此在 `embed-done` 消费者的批处理逻辑中合并同一批次文档的所有 chunks 为一次 API 调用，减少 HTTP 开销
