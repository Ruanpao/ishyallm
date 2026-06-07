package ruanpao.ishyallm.security.dto;

public record RegisterRequest(
        String username,
        String password,
        String name,
        String department,
        String role
) {
}
