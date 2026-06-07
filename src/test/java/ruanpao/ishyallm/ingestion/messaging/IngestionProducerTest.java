package ruanpao.ishyallm.ingestion.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionProducerTest {

    private static EmbeddedKafkaKraftBroker embeddedKafka;
    private IngestionProducer producer;
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
        producer = new IngestionProducer(bootstrapServers);
    }

    @Test
    void shouldSendParseDoneEvent() throws Exception {
        var event = new ParseDoneEvent(
                "DOC-001", "guide.pdf", "2024", "儿科", "doctor-1",
                List.of(new ChunkData("c1", "content", 1, "章节1", 1)));

        producer.sendParseDone(event);

        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of("pdf-parse-done"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThan(0);

            var consumed = objectMapper.readValue(
                    records.iterator().next().value(), ParseDoneEvent.class);
            assertThat(consumed.docId()).isEqualTo("DOC-001");
            assertThat(consumed.title()).isEqualTo("guide.pdf");
        }
    }

    @Test
    void shouldSendEmbedDoneEvent() throws Exception {
        var event = new EmbedDoneEvent(
                "c1", "DOC-001", "content",
                List.of(0.1, 0.2, 0.3), 1, "儿科");

        producer.sendEmbedDone(event);

        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of("embed-done"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThan(0);

            var consumed = objectMapper.readValue(
                    records.iterator().next().value(), EmbedDoneEvent.class);
            assertThat(consumed.chunkId()).isEqualTo("c1");
        }
    }

    @Test
    void shouldSendToDlq() {
        producer.sendToDlq("DOC-001", "parse error: invalid PDF");

        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of("ingestion-dlq"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThan(0);
            assertThat(records.iterator().next().value()).contains("parse error");
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
