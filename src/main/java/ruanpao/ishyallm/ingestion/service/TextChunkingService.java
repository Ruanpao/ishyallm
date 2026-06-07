package ruanpao.ishyallm.ingestion.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TextChunkingService {

    private static final int MAX_TOKENS_PER_CHUNK = 512;

    public record Chunk(
            String content,
            int seqOrder
    ) {}

    public List<Chunk> chunk(String text, int startSeq) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder currentChunk = new StringBuilder();
        int seq = startSeq;

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            int currentTokens = estimateTokens(currentChunk.toString());
            int paraTokens = estimateTokens(trimmed);

            if (currentTokens + paraTokens <= MAX_TOKENS_PER_CHUNK && currentChunk.length() > 0) {
                currentChunk.append("\n\n").append(trimmed);
            } else {
                if (currentChunk.length() > 0) {
                    chunks.add(new Chunk(currentChunk.toString(), seq++));
                    currentChunk = new StringBuilder();
                }

                if (paraTokens > MAX_TOKENS_PER_CHUNK) {
                    // Split long paragraph by sentence boundaries
                    for (String sentence : splitLongText(trimmed)) {
                        if (currentChunk.length() > 0) {
                            chunks.add(new Chunk(currentChunk.toString(), seq++));
                            currentChunk = new StringBuilder();
                        }
                        currentChunk.append(sentence);
                    }
                } else {
                    currentChunk.append(trimmed);
                }
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(new Chunk(currentChunk.toString(), seq));
        }

        return chunks;
    }

    private List<String> splitLongText(String text) {
        List<String> parts = new ArrayList<>();
        // Simple split at space boundaries for long text
        String[] words = text.split(" ");
        StringBuilder part = new StringBuilder();

        for (String word : words) {
            if (estimateTokens(part.toString()) + 1 <= MAX_TOKENS_PER_CHUNK) {
                if (part.length() > 0) part.append(" ");
                part.append(word);
            } else {
                parts.add(part.toString());
                part = new StringBuilder(word);
            }
        }
        if (part.length() > 0) {
            parts.add(part.toString());
        }
        return parts;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // Rough estimation: English ≈ 4 chars/token, Chinese ≈ 2 chars/token
        return (int) Math.ceil(text.length() / 4.0);
    }
}
