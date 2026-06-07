package ruanpao.ishyallm.retrieval;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

public class VectorRepository {

    private final JdbcTemplate jdbc;

    public VectorRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public record SearchResult(
            String chunkId, String docId, String content,
            int pageNumber, String department, double score
    ) {}

    public void insert(String chunkId, String docId, String content,
                       List<Double> embedding, int pageNumber, String department) {
        String vec = "[" + String.join(",", embedding.stream().map(String::valueOf).toList()) + "]";
        jdbc.update("""
                INSERT INTO doc_chunks (chunk_id, doc_id, content, embedding, page_number, department)
                VALUES (?, ?, ?, ?::vector, ?, ?)
                """, chunkId, docId, content, vec, pageNumber, department);
    }

    public List<SearchResult> search(List<Double> queryVector, int topK) {
        String vec = "[" + String.join(",", queryVector.stream().map(String::valueOf).toList()) + "]";
        return jdbc.query("""
                SELECT chunk_id, doc_id, content, page_number, department,
                       1 - (embedding <=> ?::vector) AS score
                FROM doc_chunks
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """, (rs, row) -> new SearchResult(
                        rs.getString("chunk_id"),
                        rs.getString("doc_id"),
                        rs.getString("content"),
                        rs.getInt("page_number"),
                        rs.getString("department"),
                        rs.getDouble("score")
                ), vec, vec, topK);
    }

    public List<SearchResult> searchByDepartment(List<Double> queryVector, int topK, String department) {
        String vec = "[" + String.join(",", queryVector.stream().map(String::valueOf).toList()) + "]";
        return jdbc.query("""
                SELECT chunk_id, doc_id, content, page_number, department,
                       1 - (embedding <=> ?::vector) AS score
                FROM doc_chunks
                WHERE department = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """, (rs, row) -> new SearchResult(
                        rs.getString("chunk_id"),
                        rs.getString("doc_id"),
                        rs.getString("content"),
                        rs.getInt("page_number"),
                        rs.getString("department"),
                        rs.getDouble("score")
                ), vec, department, vec, topK);
    }
}
