package com.example.helpdesk.advisor;

import java.util.List;

import com.example.helpdesk.security.PromptInjectionDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/** 검색된 정책 문서에 숨은 간접 프롬프트 인젝션을 모델 호출 전에 차단한다. */
@Component
public class RetrievedDocumentSafetyAdvisor implements CallAdvisor {

    public static final int ORDER = 350;
    private static final Logger audit = LoggerFactory.getLogger("AI_SAFETY");
    private static final String FAILURE_RESPONSE =
            "검색된 정책 문서의 안전성을 확인할 수 없어 답변을 중단했습니다.";

    private final PromptInjectionDetector injectionDetector;

    public RetrievedDocumentSafetyAdvisor(PromptInjectionDetector injectionDetector) {
        this.injectionDetector = injectionDetector;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Object value = request.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (value instanceof List<?> values) {
            Document unsafe = values.stream()
                    .filter(Document.class::isInstance)
                    .map(Document.class::cast)
                    .filter(document -> injectionDetector.containsInjection(document.getText()))
                    .findFirst()
                    .orElse(null);
            if (unsafe != null) {
                audit.error("event=RAG_DOCUMENT_BLOCKED source={}",
                        unsafe.getMetadata().getOrDefault("source", "unknown"));
                return SafetyAdvisor.blockedResponse(
                        request, "indirect_prompt_injection", FAILURE_RESPONSE);
            }
        }
        return chain.nextCall(request);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }
}
