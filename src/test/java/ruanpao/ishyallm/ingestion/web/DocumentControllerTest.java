package ruanpao.ishyallm.ingestion.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ruanpao.ishyallm.security.jwt.JwtTokenProvider;
import ruanpao.ishyallm.common.domain.UserRole;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DocumentControllerTest {

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

    @LocalServerPort
    private int port;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private WebTestClient webTestClient;
    private String validToken;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        validToken = jwtTokenProvider.generateToken("1", "儿科", UserRole.DOCTOR);

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
    void shouldUploadPdf() {
        // Given: 一个简单的测试 PDF
        byte[] pdfContent = createSimplePdf();

        webTestClient.post()
                .uri("/api/documents/upload")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(multipart("test-guide.pdf", pdfContent, "儿科"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("test-guide.pdf")
                .jsonPath("$.department").isEqualTo("儿科")
                .jsonPath("$.status").isEqualTo("UPLOADED")
                .jsonPath("$.id").isNumber();
    }

    @Test
    void shouldRejectUploadWithoutAuth() {
        byte[] pdfContent = createSimplePdf();

        webTestClient.post()
                .uri("/api/documents/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(multipart("test.pdf", pdfContent, "儿科"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldListDocuments() {
        // Given: 先上传一个文档
        uploadTestDoc("doc1.pdf", "儿科");

        // When/Then: 列表查询
        webTestClient.get()
                .uri("/api/documents?department=儿科")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].title").isEqualTo("doc1.pdf");
    }

    @Test
    void shouldGetDocumentStatus() {
        // Given: 上传文档
        var response = webTestClient.post()
                .uri("/api/documents/upload")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(multipart("status-test.pdf", createSimplePdf(), "儿科"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .returnResult();
        long docId = Long.parseLong(
                new String(response.getResponseBodyContent()).split("\"id\":")[1].split(",")[0]);

        // When/Then: 查询状态
        webTestClient.get()
                .uri("/api/documents/" + docId + "/status")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UPLOADED");
    }

    // --- helpers ---

    private void uploadTestDoc(String filename, String department) {
        webTestClient.post()
                .uri("/api/documents/upload")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(multipart(filename, createSimplePdf(), department))
                .exchange()
                .expectStatus().isOk();
    }

    private byte[] createSimplePdf() {
        try {
            org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(25, 700);
                cs.showText("Test PDF content for upload");
                cs.endText();
            }
            var baos = new java.io.ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private org.springframework.util.MultiValueMap<String, HttpEntity<?>> multipart(String filename, byte[] content, String department) {
        var builder = new MultipartBodyBuilder();
        builder.part("file", content, MediaType.APPLICATION_PDF).filename(filename);
        builder.part("department", department);
        return builder.build();
    }
}
