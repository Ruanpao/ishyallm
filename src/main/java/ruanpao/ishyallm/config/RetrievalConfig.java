package ruanpao.ishyallm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ruanpao.ishyallm.retrieval.VectorRepository;

@Configuration
public class RetrievalConfig {

    @Bean
    @ConditionalOnProperty(name = "ishyallm.pgvector.enabled", havingValue = "true")
    public VectorRepository vectorRepository(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        // 从 JDBC URL 解析 host, port, database
        String url = jdbcUrl;
        if (url.startsWith("jdbc:")) {
            url = url.substring(5);
        }
        // url format: postgresql://localhost:5432/ishyallm
        String[] hostPortDb = url.split("//")[1].split("/");
        String[] hostPort = hostPortDb[0].split(":");
        String host = hostPort[0];
        int port = Integer.parseInt(hostPort[1]);
        String database = hostPortDb[1];
        return new VectorRepository(host, port, database, username, password);
    }
}