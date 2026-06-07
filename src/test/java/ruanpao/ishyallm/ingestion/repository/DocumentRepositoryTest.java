package ruanpao.ishyallm.ingestion.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;
import ruanpao.ishyallm.ingestion.domain.Document;
import ruanpao.ishyallm.ingestion.domain.DocStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DocumentRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ishyallm").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/ishyallm");
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("ishyallm.jwt.secret", () -> "test-secret-key-for-testing-purposes-only-32chars!!");
        registry.add("ishyallm.jwt.expiry-seconds", () -> "86400");
    }

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private DocumentRepository documentRepository;

    @BeforeEach
    void setUp() {
        databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS documents (
                    id BIGSERIAL PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    version VARCHAR(50),
                    department VARCHAR(100) NOT NULL,
                    uploaded_by VARCHAR(100) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
                    created_at TIMESTAMP NOT NULL DEFAULT NOW()
                )""").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM documents").fetch().rowsUpdated().block();
    }

    @Test
    void shouldSaveAndFindDocument() {
        Document doc = new Document("测试指南", "2024", "儿科", "doctor-1");

        StepVerifier.create(documentRepository.save(doc))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getTitle()).isEqualTo("测试指南");
                    assertThat(saved.getVersion()).isEqualTo("2024");
                    assertThat(saved.getDepartment()).isEqualTo("儿科");
                    assertThat(saved.getUploadedBy()).isEqualTo("doctor-1");
                    assertThat(saved.getStatus()).isEqualTo(DocStatus.UPLOADED);
                    assertThat(saved.getCreatedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldFindByDepartment() {
        documentRepository.save(new Document("儿科指南", "2024", "儿科", "doctor-1")).block();
        documentRepository.save(new Document("外科指南", "2024", "外科", "doctor-2")).block();

        StepVerifier.create(documentRepository.findByDepartment("儿科"))
                .assertNext(doc -> assertThat(doc.getTitle()).isEqualTo("儿科指南"))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void shouldUpdateStatus() {
        Document doc = documentRepository.save(
                new Document("指南", "2024", "内科", "doctor-1")).block();

        databaseClient.sql("UPDATE documents SET status='PARSED' WHERE id=" + doc.getId())
                .fetch().rowsUpdated().block();

        StepVerifier.create(documentRepository.findById(doc.getId()))
                .assertNext(updated -> assertThat(updated.getStatus()).isEqualTo(DocStatus.PARSED))
                .verifyComplete();
    }
}
