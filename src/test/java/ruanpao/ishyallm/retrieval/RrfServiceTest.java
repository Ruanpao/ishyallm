package ruanpao.ishyallm.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfServiceTest {

    private final RrfService rrf = new RrfService();

    @Test
    void shouldMergeTwoResultLists() {
        var vr = List.of(
                new VectorRepository.SearchResult("c1", "d1", "a", 1, "心内科", 0.95),
                new VectorRepository.SearchResult("c2", "d1", "b", 2, "心内科", 0.85));
        var er = List.of(
                new ElasticsearchRepository.SearchResult("c2", "d1", "b", "心内科", 8.5),
                new ElasticsearchRepository.SearchResult("c3", "d2", "c", "心内科", 7.2));

        var merged = rrf.merge(vr, er, 4);

        // c2 appears in both lists → highest RRF score
        assertThat(merged).hasSize(3);
        assertThat(merged.get(0).chunkId()).isEqualTo("c2");
    }

    @Test
    void shouldLimitToTopK() {
        var vr = List.of(
                new VectorRepository.SearchResult("c1", "d1", "a", 1, "儿科", 0.9),
                new VectorRepository.SearchResult("c2", "d1", "b", 1, "儿科", 0.8));
        var er = List.of(
                new ElasticsearchRepository.SearchResult("c3", "d2", "c", "儿科", 9.0));

        var merged = rrf.merge(vr, er, 2);

        assertThat(merged).hasSize(2);
    }

    @Test
    void shouldReturnEmptyForNoInput() {
        assertThat(rrf.merge(List.of(), List.of(), 8)).isEmpty();
    }
}
