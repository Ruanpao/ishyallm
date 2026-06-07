package ruanpao.ishyallm.ingestion.messaging;

import java.util.List;

public record EmbedDoneEvent(
        String chunkId,
        String docId,
        String content,
        List<Double> embedding,
        int pageNumber,
        String department
) {}
