package ruanpao.ishyallm.ingestion.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkingServiceTest {

    private final TextChunkingService chunker = new TextChunkingService();

    @Test
    void shouldReturnSingleChunkForShortText() {
        List<TextChunkingService.Chunk> chunks = chunker.chunk("Short text", 1);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo("Short text");
        assertThat(chunks.get(0).seqOrder()).isEqualTo(1);
    }

    @Test
    void shouldSplitTextAtTokenBoundary() {
        // ~100 tokens of text (each word ≈ 1 token, 100 words)
        String text = repeatWord("word ", 100);

        List<TextChunkingService.Chunk> chunks = chunker.chunk(text, 1);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo(text.trim());
    }

    @Test
    void shouldSplitLongTextIntoMultipleChunks() {
        // ~3000 tokens, with 512 token limit → at least 5 chunks
        String text = repeatWord("word ", 2500);

        List<TextChunkingService.Chunk> chunks = chunker.chunk(text, 1);

        assertThat(chunks.size()).isGreaterThan(1);
        // Each chunk should not exceed the token limit significantly
        assertThat(countTokens(chunks.get(0).content())).isLessThanOrEqualTo(600);
    }

    @Test
    void shouldAssignSequentialOrderNumbers() {
        String text = repeatWord("word ", 200);

        List<TextChunkingService.Chunk> chunks = chunker.chunk(text, 5);

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).seqOrder()).isEqualTo(5 + i);
        }
    }

    @Test
    void shouldHandleEmptyText() {
        List<TextChunkingService.Chunk> chunks = chunker.chunk("", 1);
        assertThat(chunks).isEmpty();
    }

    private String repeatWord(String word, int count) {
        return java.util.stream.Stream.generate(() -> word)
                .limit(count)
                .collect(java.util.stream.Collectors.joining());
    }

    private int countTokens(String text) {
        // Rough token estimation: 1 token ≈ 4 chars for English
        return text.length() / 4;
    }
}
