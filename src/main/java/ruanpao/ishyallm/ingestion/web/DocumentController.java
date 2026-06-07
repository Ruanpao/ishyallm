package ruanpao.ishyallm.ingestion.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ruanpao.ishyallm.ingestion.domain.Document;
import ruanpao.ishyallm.ingestion.messaging.ChunkData;
import ruanpao.ishyallm.ingestion.messaging.IngestionProducer;
import ruanpao.ishyallm.ingestion.messaging.ParseDoneEvent;
import ruanpao.ishyallm.ingestion.repository.DocumentRepository;
import ruanpao.ishyallm.ingestion.service.PdfParserService;
import ruanpao.ishyallm.ingestion.service.TextChunkingService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final PdfParserService pdfParserService;
    private final TextChunkingService textChunkingService;
    private IngestionProducer producer;

    public DocumentController(DocumentRepository documentRepository,
                              PdfParserService pdfParserService,
                              TextChunkingService textChunkingService) {
        this.documentRepository = documentRepository;
        this.pdfParserService = pdfParserService;
        this.textChunkingService = textChunkingService;
    }

    @Autowired(required = false)
    public void setProducer(IngestionProducer producer) {
        this.producer = producer;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Object>> upload(@RequestPart("file") Mono<Part> filePartMono,
                                                @RequestPart("department") Mono<String> departmentMono) {
        return Mono.zip(filePartMono, departmentMono)
                .flatMap(tuple -> {
                    Part part = tuple.getT1();
                    String dept = tuple.getT2();
                    String filename = part instanceof FilePart ? ((FilePart) part).filename() : "unknown.pdf";
                    String docId = "DOC-" + UUID.randomUUID().toString().substring(0, 8);

                    // 简单去重：按文件名
                    return documentRepository.existsByTitle(filename)
                            .flatMap(exists -> {
                                if (exists) {
                                    return Mono.just(ResponseEntity.status(409)
                                            .body((Object) "Document already exists: " + filename));
                                }
                                return processUpload(part, dept, filename, docId);
                            });
                });
    }

    private Mono<ResponseEntity<Object>> processUpload(Part part, String dept,
                                                        String filename, String docId) {
        return part.content()
                            .reduce(new ByteArrayOutputStream(), (baos, buf) -> {
                                byte[] bytes = new byte[buf.readableByteCount()];
                                buf.read(bytes);
                                try { baos.write(bytes); } catch (IOException ignored) {}
                                return baos;
                            })
                            .flatMap(baos -> {
                                try {
                                    String text = pdfParserService.extractText(baos.toByteArray());
                                    var chunks = textChunkingService.chunk(text, 1);

                                    // Kafka 可用时异步投递
                                    if (producer != null) {
                                        List<ChunkData> chunkDataList = chunks.stream()
                                                .map(c -> new ChunkData(
                                                        "chunk-" + UUID.randomUUID().toString().substring(0, 8),
                                                        c.content(), 0, "", c.seqOrder()))
                                                .toList();
                                        producer.sendParseDone(new ParseDoneEvent(
                                                docId, filename, null, dept, "unknown", chunkDataList));
                                    }

                                    return documentRepository.save(new Document(filename, null, dept, "unknown"))
                                            .map(doc -> ResponseEntity.ok((Object) doc));
                                } catch (IOException e) {
                                    return Mono.just(ResponseEntity.badRequest()
                                            .body((Object) "Failed to parse PDF: " + e.getMessage()));
                                }
                            });
    }

    @GetMapping
    public Flux<Document> list(@RequestParam(required = false) String department) {
        if (department != null && !department.isBlank()) {
            return documentRepository.findByDepartment(department);
        }
        return documentRepository.findAll();
    }

    @GetMapping("/{id}/status")
    public Mono<ResponseEntity<Object>> status(@PathVariable Long id) {
        return documentRepository.findById(id)
                .map(doc -> ResponseEntity.ok((Object) doc))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }
}
