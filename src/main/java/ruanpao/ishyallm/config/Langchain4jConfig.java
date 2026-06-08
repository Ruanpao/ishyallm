package ruanpao.ishyallm.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class Langchain4jConfig {

    @Bean("flashModel")
    @ConditionalOnProperty(name = "ishyallm.model.flash.api-key")
    public StreamingChatModel flashModel(
            @Value("${ishyallm.model.flash.api-key}") String apiKey,
            @Value("${ishyallm.model.flash.base-url}") String baseUrl,
            @Value("${ishyallm.model.flash.model-name}") String modelName,
            @Value("${ishyallm.model.flash.max-tokens:1024}") int maxTokens) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean("proModel")
    @ConditionalOnProperty(name = "ishyallm.model.pro.api-key")
    public StreamingChatModel proModel(
            @Value("${ishyallm.model.pro.api-key}") String apiKey,
            @Value("${ishyallm.model.pro.base-url}") String baseUrl,
            @Value("${ishyallm.model.pro.model-name}") String modelName,
            @Value("${ishyallm.model.pro.max-tokens:4096}") int maxTokens) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "langchain4j.community.dashscope.embedding-model.api-key")
    public dev.langchain4j.model.embedding.EmbeddingModel dashscopeEmbeddingModel(
            @Value("${langchain4j.community.dashscope.embedding-model.api-key}") String apiKey,
            @Value("${langchain4j.community.dashscope.embedding-model.model-name:text-embedding-v4}") String modelName) {
        return dev.langchain4j.community.model.dashscope.QwenEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
}
