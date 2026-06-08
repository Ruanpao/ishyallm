package ruanpao.ishyallm.security.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ruanpao.ishyallm.common.domain.UserRole;
import ruanpao.ishyallm.security.jwt.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AdminControllerTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie"))
            .withDatabaseName("test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/test");
        r.add("spring.r2dbc.username", pg::getUsername);
        r.add("spring.r2dbc.password", pg::getPassword);
        r.add("spring.datasource.url", () -> "jdbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/test");
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("ishyallm.jwt.secret", () -> "test-secret-key-for-test");
        r.add("ishyallm.jwt.expiry-seconds", () -> "86400");
    }

    @LocalServerPort private int port;
    private WebTestClient web;
    private String adminToken;
    private String doctorToken;

    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DatabaseClient db;

    @BeforeEach
    void setUp() {
        web = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        adminToken = jwtTokenProvider.generateToken("999", "管理部", UserRole.ADMIN);
        doctorToken = jwtTokenProvider.generateToken("1", "儿科", UserRole.DOCTOR);

        db.sql("""
                CREATE TABLE IF NOT EXISTS "user" (
                    id BIGSERIAL PRIMARY KEY, username VARCHAR(100) UNIQUE NOT NULL,
                    password VARCHAR(255) NOT NULL, name VARCHAR(100) NOT NULL,
                    department VARCHAR(100) NOT NULL, role VARCHAR(20) NOT NULL DEFAULT 'DOCTOR',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )""").fetch().rowsUpdated().block();
        db.sql("DELETE FROM \"user\"").fetch().rowsUpdated().block();
    }

    @Test
    void adminCanCreateDoctor() {
        web.post().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"newdoc","password":"123","name":"新医生","department":"内科","role":"DOCTOR"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("newdoc");
    }

    @Test
    void nonAdminCannotCreateDoctor() {
        web.post().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"hacker","password":"123","name":"黑客","department":"内科","role":"DOCTOR"}
                        """)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminCanListUsers() {
        web.get().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void adminCanGetStats() {
        web.get().uri("/api/admin/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.database").isEqualTo("connected");
    }
}
