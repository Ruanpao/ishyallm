package ruanpao.ishyallm.ingestion.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ruanpao.ishyallm.ingestion.domain.Document;
import ruanpao.ishyallm.ingestion.repository.DocumentRepository;
import ruanpao.ishyallm.ingestion.service.PdfParserService;
import ruanpao.ishyallm.ingestion.service.TextChunkingService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final PdfParserService pdfParserService;

    public DocumentController(DocumentRepository documentRepository,
                              PdfParserService pdfParserService) {
        this.documentRepository = documentRepository;
        this.pdfParserService = pdfParserService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Object>> upload(@RequestPart("file") Mono<Part> filePartMono,
                                                @RequestPart("department") Mono<String> departmentMono) {
        return Mono.zip(filePartMono, departmentMono)
                .flatMap(tuple -> {
                    Part part = tuple.getT1();
                    String dept = tuple.getT2();

                    // 从 FilePart 中获取文件名
                    String filename = part instanceof FilePart ? ((FilePart) part).filename() : "unknown.pdf";

                    // 读取文件内容
                    return part.content()
                            .reduce(new ByteArrayOutputStream(), (baos, buf) -> {
                                byte[] bytes = new byte[buf.readableByteCount()];
                                buf.read(bytes);
                                try { baos.write(bytes); } catch (IOException ignored) {}
                                return baos;
                            })
                            .flatMap(baos -> {
                                try {
                                    pdfParserService.extractText(baos.toByteArray());
                                    return documentRepository.save(new Document(filename, null, dept, "unknown"))
                                            .map(doc -> ResponseEntity.ok((Object) doc));
                                } catch (IOException e) {
                                    return Mono.just(ResponseEntity.badRequest()
                                            .body((Object) "Failed to parse PDF: " + e.getMessage()));
                                }
                            });
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
