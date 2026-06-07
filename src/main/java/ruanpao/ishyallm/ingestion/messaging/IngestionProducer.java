package ruanpao.ishyallm.ingestion.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import ruanpao.ishyallm.common.constant.KafkaTopics;

import java.util.Map;

public class IngestionProducer {

    private static final Logger log = LoggerFactory.getLogger(IngestionProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public IngestionProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // For direct test use (bypasses Spring)
    IngestionProducer(String bootstrapServers) {
        var props = Map.<String, Object>of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        this.kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        this.objectMapper = new ObjectMapper();
    }

    public void sendParseDone(ParseDoneEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.PDF_PARSE_DONE, event.docId(), json);
            log.info("Sent ParseDoneEvent for doc={}", event.docId());
        } catch (Exception e) {
            log.error("Failed to send ParseDoneEvent for doc={}", event.docId(), e);
            sendToDlq(event.docId(), e.getMessage());
        }
    }

    public void sendEmbedDone(EmbedDoneEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.EMBED_DONE, event.chunkId(), json);
        } catch (Exception e) {
            log.error("Failed to send EmbedDoneEvent for chunk={}", event.chunkId(), e);
        }
    }

    public void sendToDlq(String docId, String errorMessage) {
        try {
            String json = objectMapper.writeValueAsString(
                    Map.of("docId", docId, "error", errorMessage, "timestamp", System.currentTimeMillis()));
            kafkaTemplate.send(KafkaTopics.INGESTION_DLQ, docId, json);
        } catch (Exception e) {
            log.error("Failed to send to DLQ for doc={}", docId, e);
        }
    }
}
