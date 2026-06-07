package ruanpao.ishyallm.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ruanpao.ishyallm.retrieval.VectorRepository;

@Configuration
public class RetrievalConfig {

    @Bean
    @ConditionalOnProperty(name = "ishyallm.pgvector.enabled", havingValue = "true")
    public VectorRepository vectorRepository() {
        return new VectorRepository("localhost", 5432, "ishyallm", "postgres", "postgres");
    }
}
