package ruanpao.ishyallm.rag;

public record ChatMessage(
        String role,
        String content,
        String sources
) {
    public ChatMessage(String role, String content) {
        this(role, content, null);
    }
}
