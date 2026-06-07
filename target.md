智能医疗知识库问答系统
全栈开发
核心技术栈：Spring WebFlux + Langchain4j + PGVector + Elasticsearch + Kafka
针对 LLM 在医疗垂直领域易产生幻觉及知识滞后的痛点，设计并落地支持复杂医学 PDF 解析与精准溯源的RAG（检索增强生成）系统。
非阻塞网关与流式响应：采用 Spring WebFlux（Netty + epoll） 重构全局网关以应对大模型长耗时响应。引入 SSE 协议与 HTTP Chunked 机制支撑海量长连接，将首字响应时间稳定压降至 300ms 以内。
异步编排与资源隔离：引入 Kafka 构建异步数据摄入流水线，缓解文档切分与 Embedding 特征提取的 CPU消耗。利用自定义线程池对 I/O 与 CPU 密集型任务进行逻辑隔离；并针对解析异常引入 DLQ（死信队列）实现消息兜底。
双路召回与 RRF 重排：为提升生僻医疗名词检索精度，搭建 PGVector + Elasticsearch（真 BM25） 混合架构。在二次重排阶段基于 RRF 与词项重叠算法进行精细打分，将问答幻觉率从 35% 压降致 3% 以
路由策略与越权防御：引入 Query Rewrite 解决多轮对话指代缺失，采用大小模型路由规避单模型延迟瓶颈；写入时强制绑定患者标识，检索时利用 Metadata 预过滤进行物理拦截，彻底阻断用户越权串扰。

以下按功能分组列出整个项目需要的所有 Maven 包（不含已在 pom.xml 中的）：

  � 认证与安全

  1. java-jwt (com.auth0)
  2. spring-boot-starter-security
  3. spring-security-crypto
  4. lombok

  � PDF 解析与嵌入

  5. pdfbox (org.apache.pdfbox)

  � Langchain4j + LLM 集成

  6. langchain4j-spring-boot-starter
  7. langchain4j-deepseek4j
  8. langchain4j-dashscope-embedding

  � 搜索引擎

  9. elasticsearch-java (co.elastic.clients)

  � 消息队列

  10. kafka-clients (org.apache.kafka)
  11. spring-kafka

  �️ 向量存储

  12. pgvector (JDBC 驱动扩展)

  � 集成测试

  13. testcontainers-bom
  14. testcontainers (org.testcontainers)
  15. testcontainers-postgresql
  16. testcontainers-elasticsearch
  17. testcontainers-kafka

  � 监控

  18. spring-boot-starter-actuator

  ---
  总共新加 18 个包。 你要全部导入还是先只装当前切片 1 需要的？切片 1 只需要前 4
  个（java-jwt、spring-boot-starter-security、spring-security-crypto、lombok）。

  导入注意事项

  � 关键注意事项

  1. Spring Boot + Spring Security 的兼容性
  - Spring Boot 4.0（你当前版本）刚发布不久，某些包的版本可能不兼容
  - 建议：不要指定版本号的依赖（如 spring-boot-starter-security），让 Spring Boot 的 parent BOM 统一管理版本
  - 需要指定版本号的包（无 BOM 管理）：
    - java-jwt 4.4.0
    - pdfbox
    - elasticsearch-java
    - pgvector
    - langchain4j 全家桶
    - testcontainers 全家桶

  2. Spring Boot 4.0 + WebFlux → spring-boot-starter-security 的坑
  - 你用的是 WebFlux（不是 WebMVC），spring-boot-starter-security 会默认加入 Servlet 过滤器链
  - 必须 同时在 WebFlux 端配置 SecurityWebFilterChain，否则登录接口会被自动拦截
  - 需要额外加配置类禁用默认的 security 配置，改用 WebFlux 风格的认证

  3. java-jwt 4.4.0
  - Auth0 的库，稳定成熟，无需额外配置
  - 密钥长度：HMAC256 需要至少 256 bits（32 字符） 的密钥，否则运行时报错

  4. Lombok
  - 需要在 IDE 中安装 Lombok 插件（否则 IDE 报红但 Maven 编译能过）
  - @Data, @Builder, @Slf4j 等注解会大幅减少代码量

  5. langchain4j + Spring Boot 4.0
  - Langchain4j 目前最高支持到 Spring Boot 3.x，可能还没适配 Boot 4.0
  - 如果编译报错，可能需要降级 Spring Boot 到 3.x 或用 Langchain4j 的快照版
  - 切片 1 用不到它，可以后面实际需要时再试

  6. Testcontainers
  - 需要 Docker Desktop 运行中（Windows 下必须启动 Docker Desktop）
  - Testcontainers 全家桶建议通过 BOM 统一版本管理

  7. pgvector
  - 不是独立的包，而是 PostgreSQL 的扩展
  - Maven 需要引入 PG JDBC 驱动的扩展包（你已经有了 r2dbc-postgresql，但向量操作还需要额外的依赖）
  - 可以通过 Spring JDBC 的 JdbcTemplate 配合 pgvector JDBC 扩展来操作

  8. Elasticsearch Java Client 8.x
  - 和 spring-boot-starter-webflux 都依赖 react-core / react-netty，注意版本冲突

  9. Kafka
  - spring-kafka 会自己拉对应版本的 kafka-clients，但只加一个就够了（保持版本一致）
  - WebFlux 项目建议用 Reactive Kafka（spring-kafka 的响应式支持），但普通 spring-kafka 也能用

  ---
  总结：建议分阶段导入

  ┌────────────────┬──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
  │      切片      │                                               需要导入的包                                               │
  ├────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ 切片 1（当前） │ java-jwt + spring-boot-starter-security + spring-security-crypto + lombok + spring-boot-starter-actuator │
  ├────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ 切片 2         │ pdfbox                                                                                                   │
  ├────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ 切片 3-4       │ elasticsearch-java + pgvector + kafka-clients / spring-kafka + testcontainers 全家桶                     │
  ├────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
  │ 切片 5         │ langchain4j 全家桶（如果 Boot 4.0 不兼容需要降级 Boot）                                                  │
  └────────────────┴──────────────────────────────────────────────────────────────────────────────────────────────────────────┘

  建议现在只装切片 1 的 5 个包，保证当前能跑通，后续需要再加。