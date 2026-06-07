package ruanpao.ishyallm.common.dto;

public record ErrorResponse(
        int status,
        String message
) {
}
