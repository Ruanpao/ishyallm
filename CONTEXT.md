# 智能医疗知识库问答系统 (IshyaLLM)

## 项目定位
基于 RAG 的医疗垂直领域知识库问答系统，解决 LLM 在医疗场景下的幻觉与知识滞后问题。

## 核心技术栈
- **框架**: Spring Boot 4.0 + Spring WebFlux (Netty + epoll)
- **LLM 编排**: Langchain4j
- **聊天模型**: DeepSeek API
- **Embedding**: 千问（DashScope API）
- **向量数据库**: PGVector
- **搜索引擎**: Elasticsearch (BM25)
- **消息队列**: Kafka + DLQ
- **部署**: 单 JAR 包 2~3 实例，Nginx 负载均衡

## 基础设施
- PG/ES/Kafka 全部通过本地 Docker 部署
- PGVector 索引类型：HNSW，距离度量：余弦距离

## 数据管道 (Kafka)
- 双 Topic 设计：`pdf-parse-done` → `embed-done`
- DLQ Topic：`ingestion-dlq`
- 消息粒度：批量发送（整份文档的 chunks 一批处理）
- 分区数：2（与实例数对应，每个实例消费一个 partition）

## 线程池与并发
- CPU 密集池（PDF 解析）：core=2, max=2
- IO 密集池（Embedding API/ES/PG）：core=4, max=8
- Kafka 消费者并发：2

## 模型路由
- 小模型（简单问题）：DeepSeek V4 Flash
- 大模型（复杂问题）：DeepSeek V4 Pro
- 路由策略：关键词匹配 + 问题长度（≤10 字走 Flash，>20 字走 Pro，中间按关键词判定）
- 关键词列表由初始版本 + 后期维护

## 检索与重排
- 双路召回：ES(BM25) + PGVector(语义)
- RRF 重排：各取 Top-20，合并后取 Top-8 送入 LLM

## 前端与流式
- 前端：Vue 3 + Vite
- 流式：SSE (Server-Sent Events)
- Nginx 必须配置 ip_hash 或 sticky session，保证 SSE 长连接始终路由到同一实例
- 页面结构：
  - `/login` — 登录页
  - `/chat` — 对话页（核心：对话窗口 + 检索结果展示 + 页码溯源 + 历史对话）
  - `/documents` — 文档管理（PDF 上传含科室字段、列表筛选）
  - `/admin` — 管理后台（仅管理员：用户管理、路由关键词维护、系统监控）

## 已明确决策
- 单一 JAR 包多实例部署，不拆微服务
- LLM 和 Embedding 全部走云端 API
- Embedding / 聊天模型供应商已确定（千问 / DeepSeek）
- PDF 解析：纯 Java 方案（PDFBox），数据不出域
- 知识库维度：按科室隔离，全院共享知识库文档
- 溯源精度：页码级别
- 模型路由：关键词 + 问题长度的轻量级路由策略
- 多轮对话：深度诊疗对话 + Query Rewrite (合并历史上下文)
- 使用范围：纯医生端工具
- 分块策略：混合递归（按章节优先，超长递归），maxSegmentSize = 512 tokens
- 去重策略：按文档标题 + 版本号去重
- 认证：自建账号密码 + JWT，角色（医生/管理员）
- 前端：Vue 3 + Vite，4 个核心页面
- 部署提醒：Nginx 配置 ip_hash 或 sticky session 以支持 SSE 长连接
