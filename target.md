智能医疗知识库问答系统
全栈开发
核心技术栈：Spring WebFlux + Langchain4j + PGVector + Elasticsearch + Kafka
针对 LLM 在医疗垂直领域易产生幻觉及知识滞后的痛点，设计并落地支持复杂医学 PDF 解析与精准溯源的RAG（检索增强生成）系统。
非阻塞网关与流式响应：采用 Spring WebFlux（Netty + epoll） 重构全局网关以应对大模型长耗时响应。引入 SSE 协议与 HTTP Chunked 机制支撑海量长连接，将首字响应时间稳定压降至 300ms 以内。
异步编排与资源隔离：引入 Kafka 构建异步数据摄入流水线，缓解文档切分与 Embedding 特征提取的 CPU消耗。利用自定义线程池对 I/O 与 CPU 密集型任务进行逻辑隔离；并针对解析异常引入 DLQ（死信队列）实现消息兜底。
双路召回与 RRF 重排：为提升生僻医疗名词检索精度，搭建 PGVector + Elasticsearch（真 BM25） 混合架构。在二次重排阶段基于 RRF 与词项重叠算法进行精细打分，将问答幻觉率从 35% 压降致 3% 以
路由策略与越权防御：引入 Query Rewrite 解决多轮对话指代缺失，采用大小模型路由规避单模型延迟瓶颈；写入时强制绑定患者标识，检索时利用 Metadata 预过滤进行物理拦截，彻底阻断用户越权串扰。