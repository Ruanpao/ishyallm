package ruanpao.ishyallm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import dev.langchain4j.model.embedding.EmbeddingModel;
import ruanpao.ishyallm.ingestion.messaging.IngestionConsumer;
import ruanpao.ishyallm.ingestion.messaging.IngestionProducer;

@Configuration
@ConditionalOnProperty(name = "ishyallm.kafka.enabled", havingValue = "true")
public class IngestionConfig {

    @Bean
    public IngestionProducer ingestionProducer(KafkaTemplate<String, String> kafkaTemplate,
                                               ObjectMapper objectMapper) {
        return new IngestionProducer(kafkaTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "ishyallm.ingestion.consumer.enabled", havingValue = "true", matchIfMissing = false)
    public IngestionConsumer ingestionConsumer(EmbeddingModel embeddingModel,
                                               IngestionProducer producer,
                                               ObjectMapper objectMapper,
                                               @Value("${spring.kafka.bootstrap-servers:}") String bootstrapServers) {
        return new IngestionConsumer(embeddingModel, producer, objectMapper, bootstrapServers);
    }
}
