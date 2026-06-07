package ruanpao.ishyallm.ingestion.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import ruanpao.ishyallm.ingestion.service.EmbeddingService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionEndToEndTest {

    private static EmbeddedKafkaKraftBroker embeddedKafka;
    private IngestionProducer producer;
    private IngestionConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String bs;

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
        bs = embeddedKafka.getBrokersAsString();
        producer = new IngestionProducer(bs);

        EmbeddingService stub = text -> List.of(0.1, 0.2, 0.3, 0.4, 0.5);
        consumer = new IngestionConsumer(stub, producer);
        consumer.start(bs);
    }

    @Test
    void endToEndParseDoneToEmbedDone() throws Exception {
        var chunks = List.of(
                new ChunkData("c1", "高血压定义", 1, "定义", 1),
                new ChunkData("c2", "诊断标准", 2, "诊断", 2));
        producer.sendParseDone(new ParseDoneEvent(
                "DOC-001", "高血压指南.pdf", "2024", "心内科", "doctor-1", chunks));

        Thread.sleep(3000);

        try (var verifier = createConsumer("v-" + UUID.randomUUID().toString().substring(0, 8))) {
            verifier.subscribe(List.of("embed-done"));
            ConsumerRecords<String, String> records = verifier.poll(Duration.ofSeconds(5));
            assertThat(records.count()).isEqualTo(2);

            var it = records.iterator();
            var first = objectMapper.readValue(it.next().value(), EmbedDoneEvent.class);
            assertThat(first.docId()).isEqualTo("DOC-001");
            assertThat(first.embedding()).hasSize(5);
        }
    }

    @Test
    void failedParseGoesToDlq() throws Exception {
        var senderProps = Map.<String, Object>of(
                "bootstrap.servers", bs,
                "key.serializer", StringSerializer.class,
                "value.serializer", StringSerializer.class
        );
        var sender = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(senderProps));
        sender.send("pdf-parse-done", "DOC-002", "invalid-json").get(5, java.util.concurrent.TimeUnit.SECONDS);

        Thread.sleep(2000);

        try (var verifier = createConsumer("dlq-" + UUID.randomUUID().toString().substring(0, 8))) {
            verifier.subscribe(List.of("ingestion-dlq"));
            ConsumerRecords<String, String> records = verifier.poll(Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThan(0);
        }
    }

    private KafkaConsumer<String, String> createConsumer(String groupId) {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bs,
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        ));
    }
}
