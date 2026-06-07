package ruanpao.ishyallm.rag;

import reactor.core.publisher.Flux;
import ruanpao.ishyallm.retrieval.ElasticsearchRepository;
import ruanpao.ishyallm.retrieval.RrfService;
import ruanpao.ishyallm.retrieval.VectorRepository;

public class RagService {

    private final QueryRewriteService queryRewrite;
    private final ChatLanguageModel chatModel;
    private final VectorRepository vectorRepo;
    private final ElasticsearchRepository esRepo;
    private final RrfService rrf;

    public RagService(QueryRewriteService queryRewrite, ChatLanguageModel chatModel,
                      VectorRepository vectorRepo, ElasticsearchRepository esRepo,
                      RrfService rrf) {
        this.queryRewrite = queryRewrite;
        this.chatModel = chatModel;
        this.vectorRepo = vectorRepo;
        this.esRepo = esRepo;
        this.rrf = rrf;
    }

    public Flux<String> ask(String userQuery, String historyContext, String department) {
        String rewritten = queryRewrite.rewrite(userQuery, historyContext);
        String contextChunks = "";

        if (vectorRepo != null && esRepo != null && rrf != null) {
            try {
                // 暂用简化检索：仅向量检索（ES 需要独立容器连接）
                var vr = vectorRepo.searchByDepartment(
                        dummyVector(), 8, department);
                var er = esRepo != null ? esRepo.search(rewritten, 8)
                        : java.util.Collections.<ElasticsearchRepository.SearchResult>emptyList();
                var ranked = rrf.merge(vr, er, 8);

                var sb = new StringBuilder();
                for (int i = 0; i < ranked.size(); i++) {
                    var r = ranked.get(i);
                    sb.append("[").append(i + 1).append("] ")
                            .append(r.content()).append("\n");
                }
                contextChunks = sb.toString();
            } catch (Exception e) {
                contextChunks = "";
            }
        }

        String prompt = buildPrompt(rewritten, contextChunks);
        return chatModel.chat(prompt);
    }

    private String buildPrompt(String query, String context) {
        if (context.isBlank()) {
            return "请回答以下医学问题：\n" + query;
        }
        return "请基于以下医学文献片段回答问题。引用格式：[序号]\n\n"
                + "文献片段：\n" + context + "\n"
                + "问题：" + query + "\n\n"
                + "请用中文回答，并在相关位置标注引用来源。";
    }

    private java.util.List<Double> dummyVector() {
        return java.util.List.of(0.1, 0.1, 0.1, 0.1, 0.1);
    }
}
