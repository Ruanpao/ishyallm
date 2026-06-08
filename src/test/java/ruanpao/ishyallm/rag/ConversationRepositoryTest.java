package ruanpao.ishyallm.rag;

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
class ConversationRepositoryTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie"))
            .withDatabaseName("test").withUsername("test").withPassword("test");

    private static DataSource dataSource;
    private ConversationRepository repo;

    @BeforeAll
    static void initDb() {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/test");
        cfg.setUsername("test");
        cfg.setPassword("test");
        dataSource = new HikariDataSource(cfg);

        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS conversations (
                    id BIGSERIAL PRIMARY KEY,
                    doctor_id VARCHAR(100) NOT NULL,
                    title VARCHAR(255) NOT NULL DEFAULT '',
                    messages TEXT NOT NULL DEFAULT '[]',
                    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                )""");
    }

    @AfterAll
    static void closeDb() {
        if (dataSource instanceof HikariDataSource h && h.isRunning()) h.close();
    }

    @BeforeEach
    void setUp() {
        new JdbcTemplate(dataSource).execute("DELETE FROM conversations");
        repo = new ConversationRepository(dataSource);
    }

    @Test
    void shouldCreateAndFindConversation() {
        var msgs = List.of(new ChatMessage("user", "高血压的定义是什么"));
        repo.create("doctor-1", "高血压咨询", msgs);

        var list = repo.findByDoctorId("doctor-1");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).title()).isEqualTo("高血压咨询");
    }

    @Test
    void shouldAppendMessage() {
        var id = repo.create("doctor-1", "测试", List.of(new ChatMessage("user", "你好")));
        repo.appendMessage(id, new ChatMessage("assistant", "你好！有什么可以帮助你的？"));

        var conv = repo.findById(id);
        assertThat(conv).isNotNull();
        assertThat(conv.messages()).hasSize(2);
    }

    @Test
    void shouldIsolateByDoctor() {
        repo.create("doctor-1", "d1对话", List.of());
        repo.create("doctor-2", "d2对话", List.of());

        assertThat(repo.findByDoctorId("doctor-1")).hasSize(1);
        assertThat(repo.findByDoctorId("doctor-2")).hasSize(1);
    }
}
