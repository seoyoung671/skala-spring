package com.example.helpdesk.chat;

import java.util.List;

import com.example.helpdesk.chat.AnswerDto.Source;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 사용자의 질문을 HelpDesk ChatClient에 전달하고 답변과 실제 검색 출처를 조립한다.
 * Advisor는 문서를 모델 프롬프트에 넣어 주지만 API 출처는 자동 생성하지 않으므로,
 * 응답 context에 남은 검색 문서를 애플리케이션이 직접 꺼내 반환해야 한다.
 */
@Service
public class HelpDeskService {

    // 검색 근거가 없거나 모델 응답이 비정상일 때 반환하는 일관된 안전 문구다.
    public static final String NO_EVIDENCE_ANSWER = "제공된 문서에서 확인되지 않습니다.";

    private final ChatClient chatClient;

    public HelpDeskService(@Qualifier("helpDeskClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** 질문을 실행하고 RAG 답변 및 출처를 반환한다. */
    public AnswerDto ask(String question, String conversationId) {
        String normalizedQuestion = required(question, "질문");
        String normalizedConversationId = required(conversationId, "대화 ID");

        ChatClientResponse response = chatClient.prompt()
                .user(normalizedQuestion)
                // 같은 conversationId를 사용한 호출은 MessageChatMemoryAdvisor가
                // 앞선 대화를 찾아 현재 프롬프트에 포함한다.
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID, normalizedConversationId))
                .call()
                // content()가 아닌 전체 응답을 받아야 Advisor context를 확인할 수 있다.
                .chatClientResponse();

        return toAnswer(response);
    }

    /**
     * ChatClientResponse를 외부 API가 사용할 DTO로 바꾼다.
     * 검색 근거가 하나도 없으면 모델이 어떤 문장을 생성했더라도 안전 응답으로 대체한다.
     */
    AnswerDto toAnswer(ChatClientResponse response) {
        if (response == null) {
            return unknown();
        }

        List<Source> sources = extractSources(
                response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS));
        if (sources.isEmpty()) {
            // 모델의 사전학습 지식이나 추측이 사용자에게 전달되는 것을 막는 1차 방어선이다.
            return unknown();
        }

        String answer = extractAnswer(response);
        if (!StringUtils.hasText(answer)) {
            return unknown();
        }
        return new AnswerDto(answer, sources);
    }

    /** Advisor context의 실제 Document만 출처로 인정하고 중복 문서를 제거한다. */
    List<Source> extractSources(Object retrievedDocuments) {
        if (!(retrievedDocuments instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .map(this::toSource)
                // source/title/version이 없는 비정상 청크는 출처로 노출하지 않는다.
                .filter(this::complete)
                .distinct()
                .toList();
    }

    private Source toSource(Document document) {
        // Phase 2에서 모든 청크에 저장한 출처 메타데이터만 외부 DTO로 옮긴다.
        return new Source(
                metadata(document, "source"),
                metadata(document, "title"),
                metadata(document, "version"));
    }

    private boolean complete(Source source) {
        // 일부 값만 있는 출처는 사용자가 원문을 식별할 수 없으므로 인정하지 않는다.
        return StringUtils.hasText(source.document())
                && StringUtils.hasText(source.title())
                && StringUtils.hasText(source.version());
    }

    private String extractAnswer(ChatClientResponse response) {
        // 안전 Advisor의 조기 응답이나 공급자 오류에서는 ChatResponse/Result가
        // 비어 있을 수 있으므로 중첩 객체를 확인한 뒤 텍스트를 꺼낸다.
        if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return "";
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    private String metadata(Document document, String key) {
        // 메타데이터 타입은 Object이므로 외부 DTO에는 문자열로 정규화한다.
        Object value = document.getMetadata().get(key);
        return value == null ? "" : value.toString();
    }

    private AnswerDto unknown() {
        // 클라이언트가 null을 별도로 처리하지 않도록 항상 빈 출처 목록을 제공한다.
        return new AnswerDto(NO_EVIDENCE_ANSWER, List.of());
    }

    private String required(String value, String fieldName) {
        // 공백 질문과 대화 ID는 모델 호출 전에 차단해 불필요한 비용을 막는다.
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "이(가) 필요합니다.");
        }
        return value.strip();
    }
}
