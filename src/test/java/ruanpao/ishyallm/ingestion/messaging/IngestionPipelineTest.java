package ruanpao.ishyallm.ingestion.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionPipelineTest {

    private static final int KAFKA_PORT = 9092;
    private static final String TOPIC = "pdf-parse-done";
    private static GenericContainer<?> kafka;
    private static KafkaTemplate<String, String> kafkaTemplate;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUp() throws Exception {
        kafka = new GenericContainer<>("confluentinc/cp-kafka:latest")
                // KRaft 模式（零 ZK）
                .withEnv("KAFKA_NODE_ID", "1")
                .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
                .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:" + KAFKA_PORT + ",CONTROLLER://0.0.0.0:9093")
                .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:" + KAFKA_PORT)
                .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT")
                .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
                .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
                .withEnv("CLUSTER_ID", "kafka-cluster-1")
                .withEnv("KAFKA_CONFLUENT_SUPPORT_METRICS_ENABLE", "false")
                // 固定端口映射
                .withCreateContainerCmdModifier(cmd ->
                        ((CreateContainerCmd) cmd).withHostConfig(
                                new HostConfig().withPortBindings(
                                        new PortBinding(Ports.Binding.bindPort(KAFKA_PORT),
                                                new ExposedPort(KAFKA_PORT))
                                )
                        ))
                .withExposedPorts(KAFKA_PORT)
                .waitingFor(Wait.forLogMessage(".*started.*", 1))
                .withStartupTimeout(Duration.ofMinutes(3));
        kafka.start();

        // 等 Kafka 完全就绪
        Thread.sleep(3000);

        var producerProps = Map.<String, Object>of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + KAFKA_PORT,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    @AfterAll
    static void tearDown() {
        if (kafka != null) kafka.stop();
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

        // 发送消息（自动创建 topic）
        kafkaTemplate.send(TOPIC, "DOC-001", json).get(10, java.util.concurrent.TimeUnit.SECONDS);

        // 消费者轮询
        var consumerProps = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + KAFKA_PORT,
                ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        try (var consumer = new KafkaConsumer<String, String>(consumerProps)) {
            consumer.subscribe(List.of(TOPIC));

            ConsumerRecords<String, String> records = null;
            for (int i = 0; i < 15; i++) {
                records = consumer.poll(Duration.ofSeconds(2));
                if (records != null && records.count() > 0) break;
            }

            assertThat(records).isNotNull();
            assertThat(records.count()).isGreaterThan(0);
            var record = records.iterator().next();
            assertThat(record.key()).isEqualTo("DOC-001");

            ParseDoneEvent consumed = objectMapper.readValue(record.value(), ParseDoneEvent.class);
            assertThat(consumed.docId()).isEqualTo("DOC-001");
            assertThat(consumed.title()).isEqualTo("test-guide.pdf");
            assertThat(consumed.department()).isEqualTo("儿科");
            assertThat(consumed.chunks()).hasSize(2);
        }
    }
}
