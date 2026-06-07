package ruanpao.ishyallm.retrieval;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class VectorRepositoryTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie"))
            .withDatabaseName("test").withUsername("test").withPassword("test");

    private static DataSource dataSource;
    private VectorRepository repo;

    @BeforeAll
    static void initDb() {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/test");
        cfg.setUsername("test");
        cfg.setPassword("test");
        dataSource = new HikariDataSource(cfg);

        new JdbcTemplate(dataSource).execute("CREATE EXTENSION IF NOT EXISTS vector");
    }

    @AfterAll
    static void closeDb() {
        if (dataSource instanceof HikariDataSource h && h.isRunning()) h.close();
    }

    @BeforeEach
    void setUp() {
        repo = new VectorRepository(pg.getHost(), pg.getFirstMappedPort(),
                "test", "test", "test");
        new JdbcTemplate(dataSource).execute("DELETE FROM doc_chunks_v2");
    }

    @Test
    void shouldStoreAndSearchByCosineSimilarity() {
        repo.insert("c1", "DOC-001", "高血压诊断", List.of(0.9, 0.1, 0.1, 0.1, 0.1), 1, "心内科");
        repo.insert("c2", "DOC-001", "糖尿病治疗", List.of(0.1, 0.9, 0.1, 0.1, 0.1), 2, "心内科");

        var results = repo.search(List.of(0.85, 0.15, 0.1, 0.1, 0.1), 5);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).chunkId()).isEqualTo("c1");
        assertThat(results.get(0).score()).isPositive();
    }

    @Test
    void shouldFilterByDepartment() {
        repo.insert("c4", "DOC-003", "儿科内容", List.of(0.5, 0.5, 0.5, 0.5, 0.5), 1, "儿科");
        repo.insert("c5", "DOC-004", "外科内容", List.of(0.5, 0.5, 0.5, 0.5, 0.5), 1, "外科");

        var results = repo.searchByDepartment(List.of(0.5, 0.5, 0.5, 0.5, 0.5), 5, "儿科");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo("c4");
    }
}
