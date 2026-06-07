package ruanpao.ishyallm.rag;

import reactor.core.publisher.Flux;

public interface ChatLanguageModel {
    Flux<String> chat(String prompt);
}
