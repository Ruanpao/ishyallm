package ruanpao.ishyallm.ingestion.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ruanpao.ishyallm.common.constant.KafkaTopics;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmbedDoneConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmbedDoneConsumer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PgWriter pgWriter;
    private final EsWriter esWriter;
    private final IngestionProducer producer;
    private final String bootstrapServers;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;

    @FunctionalInterface
    public interface PgWriter {
        void write(String chunkId, String docId, String content,
                   java.util.List<Double> embedding, int pageNumber, String department);
    }

    @FunctionalInterface
    public interface EsWriter {
        void write(String chunkId, String docId, String content, String department);
    }

    public EmbedDoneConsumer(PgWriter pgWriter, EsWriter esWriter,
                             IngestionProducer producer, String bootstrapServers) {
        this.pgWriter = pgWriter;
        this.esWriter = esWriter;
        this.producer = producer;
        this.bootstrapServers = bootstrapServers;
    }

    // For test use
    EmbedDoneConsumer(PgWriter pgWriter, EsWriter esWriter, String bootstrapServers) {
        this(pgWriter, esWriter, null, bootstrapServers);
    }

    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            consumerThread = new Thread(this::run, "embed-done-consumer");
            consumerThread.setDaemon(true);
            consumerThread.start();
            log.info("EmbedDoneConsumer started");
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (consumerThread != null) consumerThread.interrupt();
    }

    private void run() {
        var props = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "embed-done-group",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(KafkaTopics.EMBED_DONE));

            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    records.forEach(record -> {
                        try {
                            EmbedDoneEvent event = objectMapper.readValue(record.value(), EmbedDoneEvent.class);
                            writeToStores(event);
                        } catch (Exception e) {
                            log.error("Failed to process embed-done: key={}", record.key(), e);
                            if (producer != null) {
                                producer.sendToDlq(record.key(), e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    log.error("Consumer error", e);
                }
            }
        }
    }

    private void writeToStores(EmbedDoneEvent event) {
        pgWriter.write(event.chunkId(), event.docId(), event.content(),
                event.embedding(), event.pageNumber(), event.department());
        esWriter.write(event.chunkId(), event.docId(), event.content(), event.department());
        log.info("Stored chunk={} to PGVector + ES", event.chunkId());
    }
}
