package ruanpao.ishyallm.retrieval;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RrfService {

    private final int k;

    public RrfService() {
        this(60);
    }

    public RrfService(int k) {
        this.k = k;
    }

    public record RankedResult(String chunkId, String docId, String content,
                               int pageNumber, String department, double score,
                               String source) {}

    public List<RankedResult> merge(List<VectorRepository.SearchResult> vectorResults,
                                    List<ElasticsearchRepository.SearchResult> esResults,
                                    int topK) {
        // 计算 RRF 分数：每个结果的 rank = 在列表中的位置(从1开始)
        Map<String, RankedResult> merged = new LinkedHashMap<>();

        // PGVector 结果
        for (int i = 0; i < vectorResults.size(); i++) {
            var r = vectorResults.get(i);
            double rrfScore = 1.0 / (k + i + 1);
            merged.merge(r.chunkId(), new RankedResult(
                    r.chunkId(), r.docId(), r.content(), r.pageNumber(), r.department(),
                    rrfScore, "vector"), (old, cur) -> new RankedResult(
                    old.chunkId(), old.docId(), old.content(), old.pageNumber(), old.department(),
                    old.score() + cur.score(), "vector+es"));
        }

        // ES 结果
        for (int i = 0; i < esResults.size(); i++) {
            var r = esResults.get(i);
            double rrfScore = 1.0 / (k + i + 1);
            merged.merge(r.chunkId(), new RankedResult(
                    r.chunkId(), r.docId(), r.content(), 0, r.department(),
                    rrfScore, "es"), (old, cur) -> new RankedResult(
                    old.chunkId(), old.docId(), old.content(), old.pageNumber(), old.department(),
                    old.score() + cur.score(), "vector+es"));
        }

        return merged.values().stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK)
                .toList();
    }
}
