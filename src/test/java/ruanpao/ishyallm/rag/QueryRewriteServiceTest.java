package ruanpao.ishyallm.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryRewriteServiceTest {

    private final QueryRewriteService rewrite = new QueryRewriteService();

    @Test
    void shouldReturnOriginalQueryWhenNoHistory() {
        String result = rewrite.rewrite("高血压的定义是什么", null);
        assertThat(result).isEqualTo("高血压的定义是什么");
    }

    @Test
    void shouldReturnOriginalQueryWhenEmptyHistory() {
        String result = rewrite.rewrite("高血压的定义是什么", "");
        assertThat(result).isEqualTo("高血压的定义是什么");
    }

    @Test
    void shouldMergeWithLastAssistantResponse() {
        String history = "用户：什么是高血压？\n助手：高血压是指动脉血压持续升高的状态。\n用户：它的诊断标准是什么？";
        String result = rewrite.rewrite("它的诊断标准是什么", history);

        assertThat(result).isNotEqualTo("它的诊断标准是什么");
        assertThat(result).contains("高血压是指动脉血压持续升高的状态");
        assertThat(result).contains("诊断标准");
    }
}
