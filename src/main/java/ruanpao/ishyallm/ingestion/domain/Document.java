package ruanpao.ishyallm.ingestion.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("documents")
public class Document {

    @Id
    private Long id;
    private String title;
    private String version;
    private String department;
    private String uploadedBy;
    private DocStatus status;
    private LocalDateTime createdAt;

    public Document() {}

    public Document(String title, String version, String department, String uploadedBy) {
        this.title = title;
        this.version = version;
        this.department = department;
        this.uploadedBy = uploadedBy;
        this.status = DocStatus.UPLOADED;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public DocStatus getStatus() { return status; }
    public void setStatus(DocStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
