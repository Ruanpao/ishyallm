package ruanpao.ishyallm.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ruanpao.ishyallm.common.domain.UserRole;
import ruanpao.ishyallm.security.domain.User;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RegistrationTest {

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
    private DatabaseClient databaseClient;

    @BeforeEach
    void setUp() {
        // 确保测试表存在（R2DBC schema.sql 可能不会自动执行）
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
    void shouldRegisterDoctorSuccessfully() {
        // When: 注册一个医生账号
        Mono<User> result = authService.register("zhangsan", "password123", "张三", "儿科", UserRole.DOCTOR);

        // Then: 注册成功，返回的用户信息正确
        StepVerifier.create(result)
                .assertNext(user -> {
                    assertThat(user.getId()).isNotNull();
                    assertThat(user.getUsername()).isEqualTo("zhangsan");
                    assertThat(user.getName()).isEqualTo("张三");
                    assertThat(user.getDepartment()).isEqualTo("儿科");
                    assertThat(user.getRole()).isEqualTo(UserRole.DOCTOR);
                    assertThat(user.getEnabled()).isTrue();
                    assertThat(user.getPassword()).isNotEqualTo("password123"); // 密码已加密
                })
                .verifyComplete();
    }

    @Test
    void shouldEncryptPassword() {
        // When
        User user = authService.register("lisi", "secret456", "李四", "内科", UserRole.DOCTOR).block();

        // Then: 密码是 BCrypt 格式（以 $2a$ 开头）
        assertThat(user).isNotNull();
        assertThat(user.getPassword()).startsWith("$2a$");

        // 验证密码匹配
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("secret456", user.getPassword())).isTrue();
    }

    @Test
    void shouldRejectDuplicateUsername() {
        // Given: 先注册一个用户
        authService.register("wangwu", "pass123", "王五", "外科", UserRole.DOCTOR).block();

        // When: 注册相同用户名
        Mono<User> duplicate = authService.register("wangwu", "anotherPass", "王五2", "外科", UserRole.DOCTOR);

        // Then: 应抛出异常
        StepVerifier.create(duplicate)
                .expectErrorMatches(e -> e.getMessage().contains("already exists") || e.getMessage().contains("duplicate"))
                .verify();
    }

}
