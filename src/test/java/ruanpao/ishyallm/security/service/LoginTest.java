package ruanpao.ishyallm.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ruanpao.ishyallm.common.domain.UserRole;
import ruanpao.ishyallm.security.dto.LoginResponse;
import ruanpao.ishyallm.security.jwt.JwtTokenProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class LoginTest {

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

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void setUp() {
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

        // 注册一个测试用户
        authService.register("testdoctor", "correct-password", "测试医生", "儿科", UserRole.DOCTOR).block();
    }

    @Test
    void shouldLoginWithValidCredentials() {
        // When: 使用正确密码登录
        Mono<LoginResponse> result = authService.login("testdoctor", "correct-password");

        // Then: 返回 LoginResponse 包含 token 和用户信息
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.token()).isNotNull().isNotEmpty();
                    assertThat(response.user().getUsername()).isEqualTo("testdoctor");
                    assertThat(response.user().getName()).isEqualTo("测试医生");
                    assertThat(response.user().getDepartment()).isEqualTo("儿科");
                    assertThat(response.user().getRole()).isEqualTo(UserRole.DOCTOR);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnValidJwtOnLogin() {
        // When
        LoginResponse response = authService.login("testdoctor", "correct-password").block();

        // Then: token 可以被 JwtTokenProvider 验证
        assertThat(response).isNotNull();
        var claims = jwtTokenProvider.verifyAndExtract(response.token());
        assertThat(claims.doctorId()).isEqualTo(response.user().getId().toString());
        assertThat(claims.department()).isEqualTo("儿科");
        assertThat(claims.role()).isEqualTo(UserRole.DOCTOR);
    }

    @Test
    void shouldRejectWrongPassword() {
        // When: 使用错误密码
        Mono<LoginResponse> result = authService.login("testdoctor", "wrong-password");

        // Then: 抛出异常
        StepVerifier.create(result)
                .expectErrorMatches(e -> e.getMessage().toLowerCase().contains("password")
                        || e.getMessage().toLowerCase().contains("credential")
                        || e.getMessage().toLowerCase().contains("invalid"))
                .verify();
    }

    @Test
    void shouldRejectNonExistentUser() {
        // When: 用户不存在
        Mono<LoginResponse> result = authService.login("nonexistent", "password123");

        // Then: 抛出异常
        StepVerifier.create(result)
                .expectErrorMatches(e -> e.getMessage().toLowerCase().contains("not found")
                        || e.getMessage().toLowerCase().contains("不存在")
                        || e.getMessage().toLowerCase().contains("invalid"))
                .verify();
    }
}
