package ruanpao.ishyallm.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import ruanpao.ishyallm.common.domain.UserRole;
import ruanpao.ishyallm.security.jwt.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatControllerTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @LocalServerPort
    private int port;
    private WebTestClient webTestClient;
    private String token;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port).build();
        token = jwtTokenProvider.generateToken("1", "心内科", UserRole.DOCTOR);
    }

    @Test
    void shouldStreamResponse() {
        webTestClient.post()
                .uri("/api/chat/stream")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\":\"高血压\",\"history\":null,\"department\":\"心内科\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    String body = new String(result.getResponseBodyContent());
                    assertThat(body).contains("测试");
                });
    }

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary
        public RagService mockRagService() {
            return new RagService(null, null) {
                @Override
                public Flux<String> ask(String query, String history, String department) {
                    return Flux.just("这是基于[1]和[2]的测试回答");
                }
            };
        }
    }
}
