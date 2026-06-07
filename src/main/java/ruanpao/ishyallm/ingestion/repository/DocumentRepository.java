package ruanpao.ishyallm.ingestion.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ruanpao.ishyallm.ingestion.domain.Document;

@Repository
public interface DocumentRepository extends R2dbcRepository<Document, Long> {

    Flux<Document> findByDepartment(String department);
}
