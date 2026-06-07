package ruanpao.ishyallm.security.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthControllerTest {

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

    @Autowired
    private DatabaseClient databaseClient;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS "user" (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(100) NOT NULL UNIQUE,
                    password VARCHAR(255) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    department VARCHAR(100) NOT NULL,
                    role VARCHAR(20) NOT NULL DEFAULT 'DOCTOR',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )
                """).fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM \"user\"").fetch().rowsUpdated().block();
    }

    @Test
    void shouldRegisterDoctorViaApi() {
        webTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "username": "newdoctor",
                            "password": "password123",
                            "name": "新医生",
                            "department": "内科",
                            "role": "DOCTOR"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.username").isEqualTo("newdoctor")
                .jsonPath("$.name").isEqualTo("新医生")
                .jsonPath("$.department").isEqualTo("内科")
                .jsonPath("$.role").isEqualTo("DOCTOR");
    }

    @Test
    void shouldLoginViaApi() {
        // Given: 先注册一个用户
        webTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "username": "logindoctor",
                            "password": "correct-pass",
                            "name": "登录医生",
                            "department": "外科",
                            "role": "DOCTOR"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

        // When: 登录
        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "username": "logindoctor",
                            "password": "correct-pass"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isNotEmpty()
                .jsonPath("$.user.username").isEqualTo("logindoctor")
                .jsonPath("$.user.name").isEqualTo("登录医生")
                .jsonPath("$.user.department").isEqualTo("外科");
    }

    @Test
    void shouldRejectLoginWithWrongPassword() {
        // Given: 注册用户
        webTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"user1","password":"correct","name":"用户1","department":"儿科","role":"DOCTOR"}
                        """)
                .exchange()
                .expectStatus().isOk();

        // When: 错误密码登录
        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"user1","password":"wrongpassword"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectRegisterWithoutRequiredFields() {
        webTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"incomplete"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }
}
