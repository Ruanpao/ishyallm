package ruanpao.ishyallm.rag;

import org.springframework.stereotype.Service;

@Service
public class QueryRewriteService {

    public String rewrite(String userQuery, String historyContext) {
        if (historyContext == null || historyContext.isBlank()) {
            return userQuery;
        }

        String[] lines = historyContext.split("\n");
        StringBuilder lastExchange = new StringBuilder();
        for (int i = Math.max(0, lines.length - 4); i < lines.length; i++) {
            lastExchange.append(lines[i]).append("\n");
        }

        // 简单的上下文合并：如果用户问题明显是追问，结合历史上下文
        if (isFollowUp(userQuery)) {
            String lastContext = extractLastContext(lines);
            if (!lastContext.isEmpty()) {
                return lastContext + "，" + userQuery;
            }
        }
        return userQuery;
    }

    private boolean isFollowUp(String query) {
        return query.startsWith("它") || query.startsWith("其")
                || query.startsWith("这个") || query.startsWith("该");
    }

    private String extractLastContext(String[] lines) {
        // 优先取最后一条助手回复
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].startsWith("助手：")) {
                String content = lines[i].substring(3).trim();
                int dotIndex = content.indexOf("。");
                return dotIndex > 0 ? content.substring(0, dotIndex) : content;
            }
        }
        return "";
    }
}
