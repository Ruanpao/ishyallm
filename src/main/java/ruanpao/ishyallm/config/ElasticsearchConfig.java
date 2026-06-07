package ruanpao.ishyallm.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ruanpao.ishyallm.retrieval.ElasticsearchRepository;

@Configuration
public class ElasticsearchConfig {

    @Bean
    @ConditionalOnProperty(name = "ishyallm.es.enabled", havingValue = "true")
    public ElasticsearchClient elasticsearchClient() {
        var restClient = RestClient.builder(new HttpHost("localhost", 9200, "http")).build();
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    @Bean
    @ConditionalOnProperty(name = "ishyallm.es.enabled", havingValue = "true")
    public ElasticsearchRepository elasticsearchRepository(ElasticsearchClient client) {
        return new ElasticsearchRepository(client);
    }
}
