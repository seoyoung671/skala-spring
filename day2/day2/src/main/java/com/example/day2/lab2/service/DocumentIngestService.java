package com.example.day2.lab2.service;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestService {

    private final VectorStore vectorStore;
    private final int chunkSize;
    private final int overlapPercent;

    public DocumentIngestService(VectorStore vectorStore,
            @Value("${lab2.rag.chunk-size:250}") int chunkSize,
            @Value("${lab2.rag.overlap-percent:0}") int overlapPercent) {
        this.vectorStore = vectorStore;
        this.chunkSize = chunkSize;
        this.overlapPercent = overlapPercent;
    }

    public record IngestResult(String source, int chunks) {}

    /** 읽기 → 분할 → 임베딩 → 저장 중 인제스트 4단계를 수행한다. */
    public IngestResult ingest(Resource resource, String source, String version) {
        // 1. 읽기 + 메타데이터: 분할 전에 넣어야 모든 청크에 유지된다.
        TextReader reader = new TextReader(resource);
        reader.getCustomMetadata().put("source", source);
        reader.getCustomMetadata().put("version", version);

        // 2. 분할
        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(120)
                .withKeepSeparator(true)
                .build()
                .apply(reader.get());
        chunks = applyOverlap(chunks);

        // 같은 출처를 지운 후 다시 넣어 재인제스트 중복을 방지한다.
        var filter = new FilterExpressionBuilder().eq("source", source).build();
        vectorStore.delete(filter);

        // 3. 임베딩, 4. 저장(VectorStore.add가 두 작업을 수행)
        vectorStore.add(chunks);
        return new IngestResult(source, chunks.size());
    }

    /** TokenTextSplitter 1.1.x는 겹침 옵션이 없어 인접 청크 문자를 직접 겹친다. */
    private List<Document> applyOverlap(List<Document> chunks) {
        if (overlapPercent <= 0 || chunks.size() < 2) {
            return chunks;
        }

        java.util.ArrayList<Document> overlapped = new java.util.ArrayList<>();
        overlapped.add(chunks.getFirst());
        for (int index = 1; index < chunks.size(); index++) {
            String previous = chunks.get(index - 1).getText();
            String current = chunks.get(index).getText();
            int overlapLength = Math.max(1, previous.length() * overlapPercent / 100);
            String prefix = previous.substring(Math.max(0, previous.length() - overlapLength));
            overlapped.add(new Document(prefix + "\n" + current, chunks.get(index).getMetadata()));
        }
        return List.copyOf(overlapped);
    }
}
