package ruanpao.ishyallm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import dev.langchain4j.model.embedding.EmbeddingModel;
import ruanpao.ishyallm.ingestion.messaging.EmbedDoneConsumer;
import ruanpao.ishyallm.ingestion.messaging.IngestionConsumer;
import ruanpao.ishyallm.ingestion.messaging.IngestionProducer;
import ruanpao.ishyallm.retrieval.ElasticsearchRepository;
import ruanpao.ishyallm.retrieval.VectorRepository;

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
    @ConditionalOnBean(VectorRepository.class)
    public EmbedDoneConsumer embedDoneConsumer(VectorRepository vectorRepo,
                                               ObjectProvider<ElasticsearchRepository> esRepoProvider,
                                               IngestionProducer producer,
                                               @Value("${spring.kafka.bootstrap-servers:}") String bs) {
        return new EmbedDoneConsumer(
                (chunkId, docId, content, embedding, pageNumber, department) ->
                        vectorRepo.insert(chunkId, docId, content, embedding, pageNumber, department),
                (chunkId, docId, content, department) -> {
                    ElasticsearchRepository esRepo = esRepoProvider.getIfAvailable();
                    if (esRepo != null) {
                        try { esRepo.index(chunkId, docId, content, department); }
                        catch (Exception e) { /* log */ }
                    }
                },
                producer, bs);
    }
}
