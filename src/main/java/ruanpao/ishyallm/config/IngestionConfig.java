package ruanpao.ishyallm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import dev.langchain4j.model.embedding.EmbeddingModel;
import ruanpao.ishyallm.ingestion.messaging.EmbedDoneConsumer;
import ruanpao.ishyallm.ingestion.messaging.IngestionConsumer;
import ruanpao.ishyallm.ingestion.messaging.IngestionProducer;
import ruanpao.ishyallm.retrieval.ElasticsearchRepository;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "ishyallm.kafka.enabled", havingValue = "true")
public class IngestionConfig {

    @Bean
    public IngestionProducer ingestionProducer(KafkaTemplate<String, String> kafkaTemplate,
                                               ObjectMapper objectMapper) {
        return new IngestionProducer(kafkaTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "ishyallm.ingestion.consumer.enabled", havingValue = "true")
    public IngestionConsumer ingestionConsumer(EmbeddingModel embeddingModel,
                                               IngestionProducer producer,
                                               ObjectMapper objectMapper,
                                               @Value("${spring.kafka.bootstrap-servers:}") String bs) {
        return new IngestionConsumer(embeddingModel, producer, objectMapper, bs);
    }

    @Bean
    @ConditionalOnProperty(name = "ishyallm.ingestion.embed-consumer.enabled", havingValue = "true")
    public EmbedDoneConsumer embedDoneConsumer(DataSource dataSource,
                                               IngestionProducer producer,
                                               @Value("${spring.kafka.bootstrap-servers:}") String bs) {
        return new EmbedDoneConsumer(
                // PGVector 写入
                (chunkId, docId, content, embedding, pageNumber, department) -> {
                    String vec = "[" + String.join(",", embedding.stream()
                            .map(String::valueOf).toList()) + "]";
                    new JdbcTemplate(dataSource).update("""
                            INSERT INTO doc_chunks_v2 (chunk_id, doc_id, content, embedding, page_number, department)
                            VALUES (?, ?, ?, ?::vector, ?, ?)
                            """, chunkId, docId, content, vec, pageNumber, department);
                },
                // ES 写入（无 ES 时跳过）
                (chunkId, docId, content, department) -> {},
                producer, bs);
    }
}
