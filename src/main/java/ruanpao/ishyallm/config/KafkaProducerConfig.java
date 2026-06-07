package ruanpao.ishyallm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import ruanpao.ishyallm.ingestion.messaging.IngestionProducer;

@Configuration
public class KafkaProducerConfig {

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    public IngestionProducer ingestionProducer(KafkaTemplate<String, String> kafkaTemplate,
                                               ObjectMapper objectMapper) {
        return new IngestionProducer(kafkaTemplate, objectMapper);
    }
}
