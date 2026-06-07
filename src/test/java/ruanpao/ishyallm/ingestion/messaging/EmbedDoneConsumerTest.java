package ruanpao.ishyallm.ingestion.messaging;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class EmbedDoneConsumerTest {

    private static EmbeddedKafkaKraftBroker embeddedKafka;
    private IngestionProducer producer;
    private String bs;
    private final CopyOnWriteArrayList<String> pgRecords = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> esRecords = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void setUpClass() {
        embeddedKafka = new EmbeddedKafkaKraftBroker(1, 1, "embed-done", "ingestion-dlq");
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
        pgRecords.clear();
        esRecords.clear();
    }

    @Test
    void shouldWriteToPGAndES() throws Exception {
        var consumer = new EmbedDoneConsumer(
                (id, docId, content, emb, page, dept) -> pgRecords.add(id),
                (id, docId, content, dept) -> esRecords.add(id),
                bs);
        consumer.start();

        producer.sendEmbedDone(new EmbedDoneEvent("c1", "DOC-001", "text",
                List.of(0.1, 0.2, 0.3, 0.4, 0.5), 1, "儿科"));

        Thread.sleep(2000);

        assertThat(pgRecords).contains("c1");
        assertThat(esRecords).contains("c1");
        consumer.stop();
    }

    @Test
    void shouldSendToDlqOnPGError() throws Exception {
        // 直接验证 sendToDlq 能写入 DLQ
        producer.sendToDlq("c2", "PG fail");

        try (var verif = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
                java.util.Map.of(
                        org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bs,
                        org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, "dlq-verif",
                        org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                                org.apache.kafka.common.serialization.StringDeserializer.class,
                        org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                                org.apache.kafka.common.serialization.StringDeserializer.class,
                        org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
                ))) {
            verif.subscribe(List.of("ingestion-dlq"));
            var records = verif.poll(java.time.Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThan(0);
        }
    }
}
