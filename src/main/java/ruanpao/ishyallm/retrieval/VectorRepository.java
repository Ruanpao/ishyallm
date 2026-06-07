package ruanpao.ishyallm.retrieval;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import java.util.List;



import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

public class VectorRepository {

    private final EmbeddingStore<TextSegment> store;

    public VectorRepository(String host, int port, String database,
                            String user, String password) {
        this(host, port, database, user, password, 5);
    }

    public VectorRepository(String host, int port, String database,
                            String user, String password, int dimension) {
        this.store = PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table("doc_chunks_v2")
                .dimension(dimension)
                .createTable(true)
                .dropTableFirst(false)
                .build();
    }

    public record SearchResult(
            String chunkId, String docId, String content,
            int pageNumber, String department, double score
    ) {}

    public void insert(String chunkId, String docId, String content,
                       List<Double> embedding, int pageNumber, String department) {
        Embedding emb = Embedding.from(embedding.stream().map(Double::floatValue).toList());
        Metadata meta = new Metadata();
        meta.put("chunkId", chunkId);
        meta.put("docId", docId);
        meta.put("pageNumber", String.valueOf(pageNumber));
        meta.put("department", department);
        store.add(emb, TextSegment.from(content, meta));
    }

    public List<SearchResult> search(List<Double> queryVector, int topK) {
        return searchWithFilter(queryVector, topK, null);
    }

    public List<SearchResult> searchByDepartment(List<Double> queryVector, int topK, String department) {
        return searchWithFilter(queryVector, topK,
                metadataKey("department").isEqualTo(department));
    }

    private List<SearchResult> searchWithFilter(List<Double> queryVector, int topK, Filter filter) {
        Embedding queryEmb = Embedding.from(queryVector.stream().map(Double::floatValue).toList());

        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmb)
                .maxResults(topK)
                .minScore(0.0)
                .filter(filter)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();

        return matches.stream().map(m -> {
            TextSegment seg = m.embedded();
            Metadata meta = seg != null ? seg.metadata() : new Metadata();
            return new SearchResult(
                    meta.getString("chunkId"),
                    meta.getString("docId"),
                    seg != null ? seg.text() : "",
                    parseIntSafe(meta.getString("pageNumber")),
                    meta.getString("department"),
                    m.score()
            );
        }).toList();
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
