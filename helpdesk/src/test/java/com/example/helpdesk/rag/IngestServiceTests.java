package com.example.helpdesk.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.example.helpdesk.config.HelpDeskProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;

class IngestServiceTests {

    private VectorStore vectorStore;
    private IngestService ingestService;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        HelpDeskProperties properties = new HelpDeskProperties(
                new HelpDeskProperties.Rag(5, 0.62),
                new HelpDeskProperties.Memory(20),
                // 짧은 테스트 문서도 청크로 만들어지도록 테스트용 최소값을 사용한다.
                new HelpDeskProperties.Ingest(100, 50, 20));
        ingestService = new IngestService(vectorStore, properties);
    }

    @Test
    void ingestDeletesOldSourceThenAddsChunksWithCitationMetadata() {
        ByteArrayResource resource = namedResource(
                "return-policy.md",
                "# 반품 규정\n\n단순 변심 반품은 상품 수령일로부터 7일 이내에 신청해야 합니다. "
                        + "상품 하자나 오배송은 수령일로부터 30일 이내에 신청할 수 있습니다.");

        IngestService.IngestResult result = ingestService.ingest(
                resource, "반품 규정", "policy", "customer-support");

        // 성공 응답만 확인하지 않고 VectorStore에 실제로 전달된 청크를 캡처한다.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);

        // 재인제스트의 핵심 조건인 delete → add 호출 순서까지 검증한다.
        InOrder order = inOrder(vectorStore);
        order.verify(vectorStore).delete(any(org.springframework.ai.vectorstore.filter.Filter.Expression.class));
        order.verify(vectorStore).add(documentsCaptor.capture());

        List<Document> stored = documentsCaptor.getValue();
        assertThat(stored).isNotEmpty();
        assertThat(stored).allSatisfy(document -> {
            assertThat(document.getMetadata())
                    .containsEntry("source", "return-policy.md")
                    .containsEntry("title", "반품 규정")
                    .containsEntry("docType", "policy")
                    .containsEntry("dept", "customer-support")
                    .containsKey("version");
        });
        assertThat(result.source()).isEqualTo("return-policy.md");
        assertThat(result.title()).isEqualTo("반품 규정");
        assertThat(result.chunks()).isEqualTo(stored.size());
    }

    @Test
    void inspectReturnsCitationScoreAndShortPreview() {
        // 검색 결과를 고정해 외부 임베딩 모델과 PGvector 없이 DTO 변환만 검증한다.
        Document hit = new Document(
                "1234567890123456789012345",
                Map.of("source", "shipping.md", "title", "배송 규정", "version", "2026-08-20"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(hit));

        List<IngestService.ChunkView> result = ingestService.inspect("제주 배송", 5);

        assertThat(result).singleElement().satisfies(chunk -> {
            assertThat(chunk.source()).isEqualTo("shipping.md");
            assertThat(chunk.title()).isEqualTo("배송 규정");
            assertThat(chunk.version()).isEqualTo("2026-08-20");
            assertThat(chunk.preview()).isEqualTo("12345678901234567890...");
        });
    }

    private ByteArrayResource namedResource(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
