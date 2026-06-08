package ruanpao.ishyallm.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ruanpao.ishyallm.common.domain.UserRole;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class JwtAuthFilterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ishyallm")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/ishyallm");
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("ishyallm.jwt.secret", () -> "test-secret-key-for-testing-purposes-only-32chars!!");
        registry.add("ishyallm.jwt.expiry-seconds", () -> "86400");
    }

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void shouldRejectRequestWithoutToken() {
        webTestClient.get()
                .uri("/api/chat/history")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectRequestWithInvalidToken() {
        webTestClient.get()
                .uri("/api/chat/history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token-here")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtTokenProvider shortLived = new JwtTokenProvider(
                "test-secret-key-for-testing-purposes-only-32chars!!", 0);
        String token = shortLived.generateToken("1", "儿科", UserRole.DOCTOR);

        webTestClient.get()
                .uri("/api/chat/history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAcceptRequestWithValidToken() {
        String token = jwtTokenProvider.generateToken("1", "儿科", UserRole.DOCTOR);

        webTestClient.get()
                .uri("/api/chat/history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Doctor-Id", "1")
                .exchange()
                .expectStatus().isEqualTo(200);
    }
}
