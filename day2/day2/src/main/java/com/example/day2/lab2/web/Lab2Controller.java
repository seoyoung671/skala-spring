package com.example.day2.lab2.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.example.day2.lab2.service.DocumentIngestService;
import com.example.day2.lab2.service.RagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lab2")
public class Lab2Controller {

    private final DocumentIngestService ingestService;
    private final RagService ragService;

    public Lab2Controller(DocumentIngestService ingestService, RagService ragService) {
        this.ingestService = ingestService;
        this.ragService = ragService;
    }

    public record AskRequest(@NotBlank String question, @Min(1) @Max(20) Integer topK) {}

    @PostMapping("/ask")
    public RagService.AnswerDto ask(@Valid @RequestBody AskRequest request) {
        return ragService.ask(request.question(), request.topK());
    }

    @GetMapping("/retrieve")
    public List<RagService.Chunk> retrieve(
            @RequestParam String q,
            @RequestParam(defaultValue = "4") int topK) {
        return ragService.retrieve(q, topK);
    }

    @PostMapping("/ingest")
    public List<DocumentIngestService.IngestResult> ingest() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/lab2-docs/*.md");
        return Arrays.stream(resources)
                .map(resource -> ingestService.ingest(
                        resource,
                        sourceName(resource),
                        "2026-07"))
                .toList();
    }

    private String sourceName(Resource resource) {
        String filename = resource.getFilename();
        return filename == null ? "unknown" : filename.replaceFirst("\\.md$", "");
    }
}
