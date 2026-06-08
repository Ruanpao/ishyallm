package ruanpao.ishyallm.rag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatHistoryController {

    private final ConversationRepository repo;

    public ChatHistoryController(ConversationRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/history")
    public List<Conversation> list(@RequestHeader("X-Doctor-Id") String doctorId) {
        try {
            return repo.findByDoctorId(doctorId);
        } catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping("/history/{id}")
    public ResponseEntity<Conversation> detail(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(repo.findById(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
