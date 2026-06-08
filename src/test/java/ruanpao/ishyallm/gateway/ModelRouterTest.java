package ruanpao.ishyallm.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRouterTest {

    private final ModelRouter router = new ModelRouter();

    @Test
    void shortQueryGoesToFlash() {
        assertThat(router.route("高血压")).isEqualTo("flash");
    }

    @Test
    void longQueryGoesToPro() {
        assertThat(router.route("请详细说明高血压的鉴别诊断方法以及治疗方案和预后")).isEqualTo("pro");
    }

    @Test
    void mediumWithFactualKeywordGoesToFlash() {
        assertThat(router.route("高血压的定义是什么")).isEqualTo("flash");
    }

    @Test
    void mediumWithReasoningKeywordGoesToPro() {
        assertThat(router.route("请问高血压的鉴别诊断方法是什么")).isEqualTo("pro");
    }

    @Test
    void mediumWithNoKeywordDefaultsToPro() {
        assertThat(router.route("我想知道一些医学信息和建议")).isEqualTo("pro");
    }
}
