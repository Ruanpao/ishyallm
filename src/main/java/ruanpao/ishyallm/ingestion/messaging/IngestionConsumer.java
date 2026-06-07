package ruanpao.ishyallm.ingestion.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
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

public class IngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(IngestionConsumer.class);

    private final EmbeddingModel embeddingModel;
    private final IngestionProducer producer;
    private final ObjectMapper objectMapper;
    private final String bootstrapServers;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;

    public IngestionConsumer(EmbeddingModel embeddingModel, IngestionProducer producer,
                             ObjectMapper objectMapper, String bootstrapServers) {
        this.embeddingModel = embeddingModel;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.bootstrapServers = bootstrapServers;
    }

    IngestionConsumer(EmbeddingModel embeddingModel, IngestionProducer producer) {
        this(embeddingModel, producer, new ObjectMapper(), "localhost:9092");
    }

    @PostConstruct
    public void start() {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            log.warn("Kafka bootstrap servers not configured, consumer not started");
            return;
        }
        if (running.compareAndSet(false, true)) {
            consumerThread = new Thread(() -> run(), "ingestion-consumer");
            consumerThread.setDaemon(true);
            consumerThread.start();
            log.info("Ingestion consumer started, connecting to {}", bootstrapServers);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        log.info("Ingestion consumer stopped");
    }

    private void run() {
        var props = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "ingestion-group",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(KafkaTopics.PDF_PARSE_DONE));

            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    records.forEach(record -> {
                        try {
                            ParseDoneEvent event = objectMapper.readValue(record.value(), ParseDoneEvent.class);
                            processEvent(event);
                        } catch (Exception e) {
                            log.error("Failed to process message: key={}", record.key(), e);
                            producer.sendToDlq(record.key(), e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    log.error("Consumer error", e);
                }
            }
        }
    }

    private void processEvent(ParseDoneEvent event) {
        for (ChunkData chunk : event.chunks()) {
            var response = embeddingModel.embed(chunk.content());
            var embedding = response.content();
            var embedEvent = new EmbedDoneEvent(
                    chunk.chunkId(), event.docId(), chunk.content(),
                    toDoubleList(embedding.vectorAsList()), chunk.pageNumber(), event.department());
            producer.sendEmbedDone(embedEvent);
        }
        log.info("Processed doc={} with {} chunks", event.docId(), event.chunks().size());
    }

    private static List<Double> toDoubleList(List<Float> floats) {
        return floats.stream().map(Float::doubleValue).toList();
    }
}
