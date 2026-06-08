package ruanpao.ishyallm.rag;

import java.time.LocalDateTime;
import java.util.List;

public record Conversation(
        Long id,
        String doctorId,
        String title,
        List<ChatMessage> messages,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
