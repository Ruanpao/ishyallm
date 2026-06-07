package ruanpao.ishyallm.ingestion.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ruanpao.ishyallm.common.constant.KafkaTopics;
import ruanpao.ishyallm.ingestion.service.EmbeddingService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class IngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(IngestionConsumer.class);

    private final EmbeddingService embeddingService;
    private final IngestionProducer producer;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;

    public IngestionConsumer(EmbeddingService embeddingService, IngestionProducer producer,
                             ObjectMapper objectMapper) {
        this.embeddingService = embeddingService;
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    // For test use
    IngestionConsumer(EmbeddingService embeddingService, IngestionProducer producer) {
        this(embeddingService, producer, new ObjectMapper());
    }

    public void start(String bootstrapServers) {
        if (running.compareAndSet(false, true)) {
            consumerThread = new Thread(() -> run(bootstrapServers), "ingestion-consumer");
            consumerThread.setDaemon(true);
            consumerThread.start();
        }
    }

    public void stop() {
        running.set(false);
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    private void run(String bootstrapServers) {
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
            List<Double> embedding = embeddingService.embed(chunk.content());
            var embedEvent = new EmbedDoneEvent(
                    chunk.chunkId(), event.docId(), chunk.content(),
                    embedding, chunk.pageNumber(), event.department());
            producer.sendEmbedDone(embedEvent);
        }
        log.info("Processed doc={} with {} chunks", event.docId(), event.chunks().size());
    }
}
