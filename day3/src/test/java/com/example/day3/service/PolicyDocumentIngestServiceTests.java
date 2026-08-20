package com.example.day3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import com.example.day3.security.PromptInjectionDetector;

class PolicyDocumentIngestServiceTests {

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void Day3_자체_정책_문서를_읽어_벡터스토어에_추가한다() throws Exception {
        VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
        doNothing().when(vectorStore).add(anyList());
        PolicyDocumentIngestService service = new PolicyDocumentIngestService(
                vectorStore, new PromptInjectionDetector());

        int chunkCount = service.ingest();

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(chunkCount).isPositive();
        assertThat(captor.getValue())
                .isNotEmpty()
                .allSatisfy(document -> {
                    assertThat(document.getMetadata().get("version")).isEqualTo("day3-v1");
                    assertThat(document.getMetadata().get("source").toString()).endsWith(".md");
                });
    }
}
