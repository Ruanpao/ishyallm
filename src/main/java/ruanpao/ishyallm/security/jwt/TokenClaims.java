package ruanpao.ishyallm.security.jwt;

import ruanpao.ishyallm.common.domain.UserRole;

import java.time.Instant;

public record TokenClaims(
        String doctorId,
        String department,
        UserRole role,
        Instant issuedAt,
        Instant expiresAt
) {
}
