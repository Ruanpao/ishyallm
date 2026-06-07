package ruanpao.ishyallm.ingestion.messaging;

import java.util.List;

public record ParseDoneEvent(
        String docId,
        String title,
        String version,
        String department,
        String uploadedBy,
        List<ChunkData> chunks
) {}
