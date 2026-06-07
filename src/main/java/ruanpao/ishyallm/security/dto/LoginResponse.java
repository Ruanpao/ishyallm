package ruanpao.ishyallm.security.dto;

import ruanpao.ishyallm.security.domain.User;

public record LoginResponse(
        String token,
        User user
) {
}
