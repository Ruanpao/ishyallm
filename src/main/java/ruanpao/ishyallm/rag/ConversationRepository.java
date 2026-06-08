package ruanpao.ishyallm.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ConversationRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConversationRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public long create(String doctorId, String title, List<ChatMessage> messages) {
        try {
            String json = mapper.writeValueAsString(messages);
            jdbc.update("""
                    INSERT INTO conversations (doctor_id, title, messages)
                    VALUES (?, ?, ?::jsonb)
                    """, doctorId, title, json);
            var id = jdbc.queryForObject("SELECT LASTVAL()", Long.class);
            return id != null ? id : 0;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create conversation", e);
        }
    }

    public Conversation findById(long id) {
        return jdbc.queryForObject("""
                SELECT id, doctor_id, title, messages, created_at, updated_at
                FROM conversations WHERE id = ?
                """, (rs, row) -> {
            try {
                List<ChatMessage> msgs = mapper.readValue(
                        rs.getString("messages"), new TypeReference<>() {});
                return new Conversation(
                        rs.getLong("id"),
                        rs.getString("doctor_id"),
                        rs.getString("title"),
                        msgs,
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, id);
    }

    public List<Conversation> findByDoctorId(String doctorId) {
        return jdbc.query("""
                SELECT id, doctor_id, title, messages, created_at, updated_at
                FROM conversations WHERE doctor_id = ? ORDER BY updated_at DESC
                """, (rs, row) -> {
            try {
                List<ChatMessage> msgs = mapper.readValue(
                        rs.getString("messages"), new TypeReference<>() {});
                return new Conversation(
                        rs.getLong("id"),
                        rs.getString("doctor_id"),
                        rs.getString("title"),
                        msgs,
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, doctorId);
    }

    public void appendMessage(long conversationId, ChatMessage message) {
        try {
            var conv = findById(conversationId);
            var newMsgs = new java.util.ArrayList<>(conv.messages());
            newMsgs.add(message);
            String json = mapper.writeValueAsString(newMsgs);
            jdbc.update("""
                    UPDATE conversations SET messages = ?::jsonb, updated_at = NOW()
                    WHERE id = ?
                    """, json, conversationId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to append message", e);
        }
    }
}
