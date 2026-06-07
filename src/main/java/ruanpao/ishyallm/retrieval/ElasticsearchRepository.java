package ruanpao.ishyallm.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ElasticsearchRepository {

    private final ElasticsearchClient client;

    public ElasticsearchRepository(ElasticsearchClient client) {
        this.client = client;
    }

    public record SearchResult(String chunkId, String docId, String content, String department, double score) {}

    public void index(String chunkId, String docId, String content, String department) throws IOException {
        client.index(i -> i
                .index("documents")
                .id(chunkId)
                .document(Map.of("chunkId", chunkId, "docId", docId, "content", content, "department", department)));
    }

    public List<SearchResult> search(String query, int topK) throws IOException {
        SearchResponse<Map> response = client.search(s -> s
                .index("documents")
                .query(q -> q
                        .match(t -> t
                                .field("content")
                                .query(query)))
                .size(topK), Map.class);

        var results = new ArrayList<SearchResult>();
        response.hits().hits().forEach(hit -> {
            Map source = hit.source();
            if (source != null) {
                results.add(new SearchResult(
                        str(source.get("chunkId")),
                        str(source.get("docId")),
                        str(source.get("content")),
                        str(source.get("department")),
                        hit.score() != null ? hit.score() : 0.0
                ));
            }
        });
        return results;
    }

    private String str(Object o) {
        return o != null ? o.toString() : "";
    }
}
