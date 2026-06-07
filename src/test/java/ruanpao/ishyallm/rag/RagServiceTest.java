package ruanpao.ishyallm.rag;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagServiceTest {

    private final QueryRewriteService rewrite = new QueryRewriteService();
    private final ChatLanguageModel mockLLM = prompt -> Flux.just("这是基于[1][2]的医学回答");

    @Test
    void shouldReturnResponseWithReferences() {
        var service = new RagService(rewrite, mockLLM, null, null, null);

        Flux<String> result = service.ask("高血压的定义是什么", null, "心内科");

        StepVerifier.create(result)
                .assertNext(text -> {
                    assertThat(text).contains("[1]");
                    assertThat(text).contains("[2]");
                })
                .verifyComplete();
    }

    @Test
    void shouldRewriteQueryWithHistory() {
        var service = new RagService(rewrite, mockLLM, null, null, null);

        Flux<String> result = service.ask("诊断标准是什么",
                "用户：什么是高血压？\n助手：高血压是指动脉血压持续升高。\n用户：诊断标准是什么",
                "心内科");

        StepVerifier.create(result)
                .assertNext(text -> assertThat(text).isNotNull())
                .verifyComplete();
    }
}
