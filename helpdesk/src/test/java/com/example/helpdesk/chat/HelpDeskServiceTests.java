package com.example.helpdesk.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;

class HelpDeskServiceTests {

    private HelpDeskService service;

    @BeforeEach
    void setUp() {
        // 이 테스트는 모델 호출이 아니라 응답과 출처 조립 규칙만 검증한다.
        service = new HelpDeskService(mock(ChatClient.class));
    }

    @Test
    void returnsAnswerWithDistinctSourcesFromAdvisorContext() {
        // 동일 문서의 서로 다른 청크가 검색된 상황을 재현한다.
        Document first = policyDocument("return-policy.md", "반품 규정", "2026-08-20");
        Document duplicate = policyDocument("return-policy.md", "반품 규정", "2026-08-20");

        AnswerDto result = service.toAnswer(response(
                "단순 변심 반품은 수령 후 7일 이내에 신청할 수 있습니다.",
                List.of(first, duplicate)));

        assertThat(result.answer()).contains("7일");
        assertThat(result.sources()).containsExactly(
                new AnswerDto.Source("return-policy.md", "반품 규정", "2026-08-20"));
    }

    @Test
    void replacesModelAnswerWhenNoEvidenceWasRetrieved() {
        // 모델이 문장을 생성했더라도 검색 문서가 없으면 신뢰하지 않아야 한다.
        AnswerDto result = service.toAnswer(response(
                "모델이 근거 없이 생성한 답변",
                List.of()));

        assertThat(result.answer()).isEqualTo(HelpDeskService.NO_EVIDENCE_ANSWER);
        assertThat(result.sources()).isEmpty();
    }

    @Test
    void ignoresRetrievedDocumentWithoutRequiredCitationMetadata() {
        // 검색된 Document라도 title/version이 없으면 표시 가능한 출처가 아니다.
        Document incomplete = new Document(
                "출처 정보가 불완전한 청크",
                Map.of("source", "unknown.md"));

        AnswerDto result = service.toAnswer(response("임의 답변", List.of(incomplete)));

        assertThat(result.answer()).isEqualTo(HelpDeskService.NO_EVIDENCE_ANSWER);
        assertThat(result.sources()).isEmpty();
    }

    private ChatClientResponse response(String answer, List<Document> documents) {
        return ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage(answer))))
                        .build())
                .context(Map.of(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, documents))
                .build();
    }

    private Document policyDocument(String source, String title, String version) {
        return new Document(
                "정책 청크",
                Map.of("source", source, "title", title, "version", version));
    }
}
