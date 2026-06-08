package ruanpao.ishyallm.config;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Langchain4jConfig {

    @Bean
    @ConditionalOnProperty(name = "langchain4j.open-ai.chat-model.api-key")
    public StreamingChatModel streamingChatModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.model-name:deepseek-chat}") String modelName) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "langchain4j.community.dashscope.embedding-model.api-key")
    @org.springframework.context.annotation.Primary
    public EmbeddingModel dashscopeEmbeddingModel(
            @Value("${langchain4j.community.dashscope.embedding-model.api-key}") String apiKey,
            @Value("${langchain4j.community.dashscope.embedding-model.model-name:text-embedding-v4}") String modelName) {
        return QwenEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
}
