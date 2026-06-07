package ruanpao.ishyallm.rag;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import ruanpao.ishyallm.retrieval.RrfService;
import ruanpao.ishyallm.retrieval.VectorRepository;

import java.util.List;

@Service
@ConditionalOnBean(StreamingChatModel.class)
public class RagService {

    private final QueryRewriteService queryRewrite;
    private final StreamingChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final VectorRepository vectorRepo;
    private final RrfService rrf;

    public RagService(QueryRewriteService queryRewrite,
                      StreamingChatModel chatModel,
                      EmbeddingModel embeddingModel,
                      VectorRepository vectorRepo,
                      RrfService rrf) {
        this.queryRewrite = queryRewrite;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.vectorRepo = vectorRepo;
        this.rrf = rrf;
    }

    public Flux<String> ask(String userQuery, String historyContext, String department) {
        String rewritten = queryRewrite.rewrite(userQuery, historyContext);
        String contextChunks = "";

        if (vectorRepo != null && rrf != null && embeddingModel != null) {
            try {
                var queryEmbedding = embeddingModel.embed(rewritten).content();
                var queryVec = toDoubleList(queryEmbedding.vectorAsList());

                var vr = vectorRepo.searchByDepartment(queryVec, 20, department);
                var ranked = rrf.merge(vr, List.of(), 8);

                var sb = new StringBuilder();
                for (int i = 0; i < ranked.size(); i++) {
                    sb.append("[").append(i + 1).append("] ")
                            .append(ranked.get(i).content()).append("\n");
                }
                contextChunks = sb.toString();
            } catch (Exception e) {
                contextChunks = "";
            }
        }

        String prompt = buildPrompt(rewritten, contextChunks);
        return streamResponse(prompt);
    }

    private Flux<String> streamResponse(String prompt) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        var request = ChatRequest.builder()
                .messages(List.of(UserMessage.userMessage(prompt)))
                .build();

        chatModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                if (token != null) sink.tryEmitNext(token);
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                sink.tryEmitComplete();
            }

            @Override
            public void onError(Throwable error) {
                sink.tryEmitError(error);
            }
        });

        return sink.asFlux();
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

    public static List<Double> toDoubleList(List<Float> floats) {
        return floats.stream().map(Float::doubleValue).toList();
    }
}
