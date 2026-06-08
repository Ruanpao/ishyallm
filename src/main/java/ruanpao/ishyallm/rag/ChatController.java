package ruanpao.ishyallm.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired(required = false)
    private RagService ragService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody ChatRequest request, ServerHttpResponse response) {
        response.getHeaders().add("Connection", "keep-alive");
        if (ragService == null) {
            return Flux.just("{\"error\":\"RAG service not available. Configure DeepSeek API key.\"}");
        }
        String department = request.department() != null ? request.department() : "";
        return ragService.ask(request.query(), request.history(), department);
    }

    public record ChatRequest(String query, String history, String department) {}
}
