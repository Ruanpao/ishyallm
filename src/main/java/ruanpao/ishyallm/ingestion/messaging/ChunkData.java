package ruanpao.ishyallm.ingestion.messaging;

public record ChunkData(
        String chunkId,
        String content,
        int pageNumber,
        String chapter,
        int seqOrder
) {}
