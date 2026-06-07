package ruanpao.ishyallm.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ruanpao.ishyallm.common.domain.UserRole;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET = "my-test-secret-key-that-is-at-least-32-chars-long!!";
    private static final long EXPIRY_SECONDS = 86400; // 24h

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, EXPIRY_SECONDS);
    }

    @Test
    void shouldGenerateAndVerifyToken() {
        // When: 签发 token
        String token = tokenProvider.generateToken("doctor-001", "儿科", UserRole.DOCTOR);

        // Then: token 不为空
        assertThat(token).isNotNull().isNotEmpty();

        // When: 解析 token
        TokenClaims claims = tokenProvider.verifyAndExtract(token);

        // Then: 所有字段正确
        assertThat(claims.doctorId()).isEqualTo("doctor-001");
        assertThat(claims.department()).isEqualTo("儿科");
        assertThat(claims.role()).isEqualTo(UserRole.DOCTOR);
        assertThat(claims.issuedAt()).isNotNull();
        assertThat(claims.expiresAt()).isNotNull();
    }

    @Test
    void shouldRejectExpiredToken() {
        // Given: 一个已过期的 token（设置 expiry=0）
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 0);
        String token = shortLived.generateToken("doctor-001", "儿科", UserRole.DOCTOR);

        // Then: 解析时应抛出异常
        assertThatThrownBy(() -> tokenProvider.verifyAndExtract(token))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void shouldRejectInvalidSignature() {
        // Given: 用不同密钥签发的 token
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "a-completely-different-secret-key-that-is-also-32-chars!!", EXPIRY_SECONDS);
        String token = otherProvider.generateToken("doctor-001", "儿科", UserRole.DOCTOR);

        // Then: 用原密钥解析应抛出异常
        assertThatThrownBy(() -> tokenProvider.verifyAndExtract(token))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThatThrownBy(() -> tokenProvider.verifyAndExtract("not-a-valid-jwt-token"))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void shouldGenerateTokenWithCorrectExpiry() {
        // When
        String token = tokenProvider.generateToken("doctor-001", "儿科", UserRole.DOCTOR);
        TokenClaims claims = tokenProvider.verifyAndExtract(token);

        // Then: 过期时间应该在签发时间之后 24h
        long diffSeconds = claims.expiresAt().getEpochSecond() - claims.issuedAt().getEpochSecond();
        assertThat(diffSeconds).isEqualTo(EXPIRY_SECONDS);
    }
}
