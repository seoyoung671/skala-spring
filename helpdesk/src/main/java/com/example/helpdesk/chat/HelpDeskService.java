package com.example.helpdesk.chat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.example.helpdesk.chat.AnswerDto.Source;
import com.example.helpdesk.advisor.AuditAdvisor;
import com.example.helpdesk.advisor.SafetyAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

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
    private final ConversationIdFactory conversationIds;
    private final ChatMemory chatMemory;

    public HelpDeskService(
            @Qualifier("helpDeskClient") ChatClient chatClient,
            ConversationIdFactory conversationIds,
            ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.conversationIds = conversationIds;
        this.chatMemory = chatMemory;
    }

    /**
     * 테넌트·사용자·세션으로 격리한 대화에서 질문을 실행한다.
     * 인증 사용자 정보는 주문과 티켓 Tool의 소유권 검사에도 전달한다.
     */
    public AnswerDto ask(
            String question,
            String tenantId,
            String userId,
            String sessionId) {
        String normalizedQuestion = required(question, "질문");
        String normalizedUserId = required(userId, "사용자 ID");
        // 클라이언트가 보낸 단일 대화 ID를 그대로 신뢰하지 않고 서버 규칙으로 만든다.
        String conversationId = conversationIds.create(tenantId, normalizedUserId, sessionId);
        AtomicBoolean toolExecuted = new AtomicBoolean(false);

        ChatClientResponse response = chatClient.prompt()
                .user(normalizedQuestion)
                // 같은 conversationId를 사용한 호출은 MessageChatMemoryAdvisor가
                // JDBC Repository에서 앞선 대화를 찾아 현재 프롬프트에 포함하고,
                // 이번 질문과 응답도 같은 ID로 다시 저장한다.
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param(AuditAdvisor.AUTHENTICATED_USER_ID, normalizedUserId))
                // 모델이 만든 orderId만 신뢰하지 않도록 인증 사용자 ID를 Tool에 함께 보낸다.
                // 실행 표시는 RAG 출처가 없는 정상 Tool 답변을 구분하는 데 사용한다.
                .toolContext(Map.of(
                        "userId", normalizedUserId,
                        "toolExecuted", toolExecuted))
                .call()
                // content()가 아닌 전체 응답을 받아야 Advisor context를 확인할 수 있다.
                .chatClientResponse();

        return toAnswer(response, toolExecuted.get());
    }

    /**
     * 모델이 생성하는 텍스트를 token 이벤트로 전달하고 마지막에 sources 이벤트를 붙인다.
     * 검색 문서나 Tool 근거가 확인되지 않은 모델 토큰은 사용자에게 내보내지 않는다.
     */
    public Flux<StreamEvent> stream(
            String question,
            String tenantId,
            String userId,
            String sessionId) {
        String normalizedQuestion = required(question, "질문");
        String normalizedUserId = required(userId, "사용자 ID");
        String conversationId = conversationIds.create(tenantId, normalizedUserId, sessionId);
        AtomicBoolean toolExecuted = new AtomicBoolean(false);
        // 스트림은 여러 콜백을 거쳐 비동기적으로 처리되므로 람다 밖의 상태를
        // 안전하게 갱신할 수 있는 Atomic 타입으로 근거와 Tool 실행 여부를 공유한다.
        AtomicBoolean trustedTokenEmitted = new AtomicBoolean(false);
        AtomicReference<List<Source>> latestSources = new AtomicReference<>(List.of());

        Flux<StreamEvent> tokens = chatClient.prompt()
                .user(normalizedQuestion)
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param(AuditAdvisor.AUTHENTICATED_USER_ID, normalizedUserId))
                .toolContext(Map.of(
                        "userId", normalizedUserId,
                        "toolExecuted", toolExecuted))
                .stream()
                .chatClientResponse()
                .handle((response, sink) -> {
                    List<Source> sources = extractSources(
                            response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS));
                    if (!sources.isEmpty()) {
                        latestSources.set(sources);
                    }

                    // RAG 문서나 Tool 실행이 확인된 응답만 브라우저에 보낸다.
                    if (!latestSources.get().isEmpty() || toolExecuted.get()) {
                        String text = extractAnswer(response);
                        if (StringUtils.hasText(text)) {
                            trustedTokenEmitted.set(true);
                            sink.next(StreamEvent.token(text));
                        }
                    }
                });

        // defer를 사용해야 스트림 조립 시점이 아니라 모든 token 처리가 끝난 시점의
        // latestSources/toolExecuted 값을 읽어 마지막 summary 이벤트를 만들 수 있다.
        return tokens.concatWith(Flux.defer(() -> {
            StreamEvent summary = StreamEvent.sources(latestSources.get(), toolExecuted.get());
            if (trustedTokenEmitted.get()) {
                return Flux.just(summary);
            }
            // 스트리밍에서도 근거 없는 모델 출력을 버리고 동일한 안전 문구를 보낸다.
            return Flux.just(StreamEvent.token(NO_EVIDENCE_ANSWER), summary);
        }));
    }

    /**
     * ChatClientResponse를 외부 API가 사용할 DTO로 바꾼다.
     * 검색 근거가 하나도 없으면 모델이 어떤 문장을 생성했더라도 안전 응답으로 대체한다.
     */
    AnswerDto toAnswer(ChatClientResponse response) {
        return toAnswer(response, false);
    }

    private AnswerDto toAnswer(ChatClientResponse response, boolean toolExecuted) {
        if (response == null) {
            return unknown();
        }

        if (Boolean.TRUE.equals(response.context().get(SafetyAdvisor.BLOCKED))) {
            String blockedAnswer = extractAnswer(response);
            return new AnswerDto(
                    StringUtils.hasText(blockedAnswer) ? blockedAnswer : NO_EVIDENCE_ANSWER,
                    List.of(), false);
        }

        List<Source> sources = extractSources(
                response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS));
        if (sources.isEmpty() && !toolExecuted) {
            // 모델의 사전학습 지식이나 추측이 사용자에게 전달되는 것을 막는 1차 방어선이다.
            return unknown();
        }

        String answer = extractAnswer(response);
        if (!StringUtils.hasText(answer)) {
            return unknown();
        }
        // Tool 답변은 실시간 DB 결과가 근거이므로 문서 출처가 없어도 정상 응답이다.
        return new AnswerDto(answer, sources, toolExecuted);
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
        return new AnswerDto(NO_EVIDENCE_ANSWER, List.of(), false);
    }

    /** 인증 사용자와 세션으로 격리된 대화 기록을 조회한다. */
    public List<HistoryMessage> history(String tenantId, String userId, String sessionId) {
        String conversationId = conversationIds.create(
                tenantId, required(userId, "사용자 ID"), sessionId);
        return chatMemory.get(conversationId).stream()
                .map(this::toHistoryMessage)
                .toList();
    }

    private HistoryMessage toHistoryMessage(Message message) {
        return new HistoryMessage(message.getMessageType().name(), message.getText());
    }

    public record HistoryMessage(String role, String content) {
    }

    private String required(String value, String fieldName) {
        // 공백 질문과 대화 ID는 모델 호출 전에 차단해 불필요한 비용을 막는다.
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "이(가) 필요합니다.");
        }
        return value.strip();
    }
}
