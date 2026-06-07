package ruanpao.ishyallm.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ElasticsearchRepositoryTest {

    @Container
    static GenericContainer<?> es = new GenericContainer<>(
            DockerImageName.parse("elasticsearch:8.19.14"))
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/").forPort(9200));

    private static ElasticsearchClient client;
    private ElasticsearchRepository repo;

    @BeforeAll
    static void setUpClass() {
        var host = es.getHost();
        var port = es.getMappedPort(9200);
        var restClient = RestClient.builder(new HttpHost(host, port, "http")).build();
        client = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    @BeforeEach
    void setUp() throws IOException {
        // 清理并重建索引
        try { client.indices().delete(d -> d.index("documents")); } catch (Exception ignored) {}
        client.indices().create(c -> c.index("documents")
                .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                .mappings(m -> m.properties("content",
                        p -> p.text(t -> t.analyzer("standard")))));
        repo = new ElasticsearchRepository(client);
    }

    @Test
    void shouldIndexAndSearchByBm25() throws IOException {
        repo.index("c1", "DOC-001", "高血压诊断标准与治疗方案", "心内科");
        repo.index("c2", "DOC-001", "糖尿病饮食控制方法", "心内科");
        repo.index("c3", "DOC-002", "高血压的预防措施", "心内科");

        client.indices().refresh(i -> i.index("documents"));

        List<ElasticsearchRepository.SearchResult> results = repo.search("高血压", 5);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).chunkId()).isIn("c1", "c3");
    }

    @Test
    void shouldReturnEmptyForNoMatch() throws IOException {
        repo.index("c4", "DOC-003", "only content", "儿科");
        client.indices().refresh(i -> i.index("documents"));

        List<ElasticsearchRepository.SearchResult> results = repo.search("不存在的内容", 5);

        assertThat(results).isEmpty();
    }
}
