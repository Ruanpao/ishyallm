package ruanpao.ishyallm.ingestion.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.*;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionPipelineTest {

    private static EmbeddedKafkaKraftBroker embeddedKafka;
    private KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String bootstrapServers;

    @BeforeAll
    static void setUpClass() {
        embeddedKafka = new EmbeddedKafkaKraftBroker(1, 1,
                "pdf-parse-done", "embed-done", "ingestion-dlq");
        embeddedKafka.afterPropertiesSet();
    }

    @AfterAll
    static void tearDownClass() {
        if (embeddedKafka != null) embeddedKafka.destroy();
    }

    @BeforeEach
    void setUp() {
        bootstrapServers = embeddedKafka.getBrokersAsString();

        var producerProps = Map.<String, Object>of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    @Test
    void shouldProduceAndConsumeParseDoneEvent() throws Exception {
        var event = new ParseDoneEvent(
                "DOC-001", "test-guide.pdf", "2024", "儿科", "doctor-1",
                List.of(
                        new ChunkData("chunk-1", "content 1", 1, "章节1", 1),
                        new ChunkData("chunk-2", "content 2", 2, "章节2", 2)
                ));
        String json = objectMapper.writeValueAsString(event);

        kafkaTemplate.send("pdf-parse-done", "DOC-001", json);

        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of("pdf-parse-done"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));

            assertThat(records.count()).isGreaterThan(0);
            var record = records.iterator().next();
            assertThat(record.key()).isEqualTo("DOC-001");

            ParseDoneEvent consumed = objectMapper.readValue(record.value(), ParseDoneEvent.class);
            assertThat(consumed.docId()).isEqualTo("DOC-001");
            assertThat(consumed.department()).isEqualTo("儿科");
            assertThat(consumed.chunks()).hasSize(2);
        }
    }

    @Test
    void shouldProduceAndConsumeEmbedDoneEvent() throws Exception {
        var event = new EmbedDoneEvent(
                "chunk-1", "DOC-001", "content text",
                List.of(0.1, 0.2, 0.3), 1, "儿科");
        String json = objectMapper.writeValueAsString(event);

        kafkaTemplate.send("embed-done", "chunk-1", json);

        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of("embed-done"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));

            assertThat(records.count()).isGreaterThan(0);
            var record = records.iterator().next();
            assertThat(record.key()).isEqualTo("chunk-1");

            EmbedDoneEvent consumed = objectMapper.readValue(record.value(), EmbedDoneEvent.class);
            assertThat(consumed.chunkId()).isEqualTo("chunk-1");
            assertThat(consumed.department()).isEqualTo("儿科");
        }
    }

    @Test
    void shouldSendToDlq() throws Exception {
        kafkaTemplate.send("ingestion-dlq", "DOC-001", "{\"error\":\"parse failed\"}");

        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of("ingestion-dlq"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));

            assertThat(records.count()).isGreaterThan(0);
            var record = records.iterator().next();
            assertThat(record.value()).contains("parse failed");
        }
    }

    private KafkaConsumer<String, String> createConsumer() {
        var props = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        return new KafkaConsumer<>(props);
    }
}
