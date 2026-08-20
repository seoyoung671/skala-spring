package com.example.day3.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.example.day3.security.PromptInjectionDetector;

@Service
public class PolicyDocumentIngestService {

    private final VectorStore vectorStore;
    private final PromptInjectionDetector injectionDetector;

    public PolicyDocumentIngestService(
            VectorStore vectorStore,
            PromptInjectionDetector injectionDetector) {
        this.vectorStore = vectorStore;
        this.injectionDetector = injectionDetector;
    }

    public int ingest() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/lab3-docs/*.md");
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(300)
                .withMinChunkSizeChars(100)
                .withKeepSeparator(true)
                .build();

        List<Document> chunks = new ArrayList<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename() == null ? "unknown.md" : resource.getFilename();
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            if (injectionDetector.containsInjection(content)) {
                throw new IllegalArgumentException(
                        "정책 문서에서 프롬프트 인젝션 패턴을 발견했습니다: " + filename);
            }
            Document document = new Document(
                    content,
                    Map.of("source", filename, "version", "day3-v1"));
            chunks.addAll(splitter.apply(List.of(document)));
        }
        vectorStore.add(chunks);
        return chunks.size();
    }
}
