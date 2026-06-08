package ruanpao.ishyallm.rag;

import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import ruanpao.ishyallm.gateway.ModelRouter;

import static org.assertj.core.api.Assertions.assertThat;

class RagServiceTest {

    private final QueryRewriteService rewrite = new QueryRewriteService();
    private final ModelRouter router = new ModelRouter();
    private final StreamingChatModel mockLLM = stubLLM("测试回答");

    @Test
    void shouldEmitFlashTagThenResponse() {
        var service = new RagService(rewrite, mockLLM, mockLLM, router);

        Flux<String> result = service.ask("高血压的定义是什么", null, "心内科");

        StepVerifier.create(result)
                .assertNext(tag -> assertThat(tag).contains("[flash]"))
                .assertNext(response -> assertThat(response).contains("测试回答"))
                .verifyComplete();
    }

    @Test
    void shouldRewriteQueryWithHistory() {
        var service = new RagService(rewrite, mockLLM, mockLLM, router);

        Flux<String> result = service.ask("诊断标准是什么",
                "用户：什么是高血压？\n助手：高血压是指动脉血压持续升高。\n用户：诊断标准是什么",
                "心内科");

        StepVerifier.create(result)
                .assertNext(tag -> assertThat(tag).contains("[flash]"))
                .assertNext(response -> assertThat(response).isNotNull())
                .verifyComplete();
    }

    @Test
    void shouldShowProTagForLongQuery() {
        var service = new RagService(rewrite, mockLLM, mockLLM, router);

        StepVerifier.create(service.ask(
                        "请详细说明高血压的鉴别诊断方法以及治疗方案和预后", null, "心内科"))
                .assertNext(tag -> assertThat(tag).contains("[pro]"))
                .thenCancel()
                .verify();
    }

    private static StreamingChatModel stubLLM(String response) {
        return new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, ChatRequestOptions options,
                             StreamingChatResponseHandler handler) {
                handler.onPartialResponse(response);
                handler.onCompleteResponse(null);
            }
        };
    }
}
