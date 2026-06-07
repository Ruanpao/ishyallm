package ruanpao.ishyallm.common.constant;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String PDF_PARSE_DONE = "pdf-parse-done";
    public static final String EMBED_DONE = "embed-done";
    public static final String INGESTION_DLQ = "ingestion-dlq";
}
