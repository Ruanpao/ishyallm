package ruanpao.ishyallm.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import ruanpao.ishyallm.common.domain.UserRole;

import java.time.Instant;
import java.util.Date;

public class JwtTokenProvider {

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expirySeconds;

    public JwtTokenProvider(String secret, long expirySeconds) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).build();
        this.expirySeconds = expirySeconds;
    }

    public String generateToken(String doctorId, String department, UserRole role) {
        Instant now = Instant.now();
        return JWT.create()
                .withClaim("doctorId", doctorId)
                .withClaim("department", department)
                .withClaim("role", role.name())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(expirySeconds)))
                .sign(algorithm);
    }

    public TokenClaims verifyAndExtract(String token) {
        try {
            DecodedJWT decoded = verifier.verify(token);
            return new TokenClaims(
                    decoded.getClaim("doctorId").asString(),
                    decoded.getClaim("department").asString(),
                    UserRole.valueOf(decoded.getClaim("role").asString()),
                    decoded.getIssuedAtAsInstant(),
                    decoded.getExpiresAtAsInstant()
            );
        } catch (TokenExpiredException e) {
            throw new JwtAuthenticationException("Token expired: " + e.getMessage(), e);
        } catch (JWTVerificationException e) {
            throw new JwtAuthenticationException("Invalid token signature: " + e.getMessage(), e);
        }
    }
}
