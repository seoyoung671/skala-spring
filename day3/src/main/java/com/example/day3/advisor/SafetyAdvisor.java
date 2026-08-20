package com.example.day3.advisor;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

import com.example.day3.security.PromptInjectionDetector;
import com.example.day3.security.SensitiveDataMasker;

@Component
public class SafetyAdvisor implements CallAdvisor {

    public static final String BLOCKED = "safety_blocked";
    public static final int ORDER = 100;

    private static final Logger audit = LoggerFactory.getLogger("AI_SAFETY");
    private static final String FAILURE_RESPONSE =
            "안전 정책에 따라 해당 요청은 처리할 수 없습니다. 주문이나 환불에 관해 다시 질문해 주세요.";
    private static final String PERSONAL_DATA_RESPONSE =
            "개인정보가 포함된 요청은 처리할 수 없습니다. 주민등록번호, 카드번호, 이메일, 전화번호를 제거해 주세요.";

    private final PromptInjectionDetector injectionDetector;
    private final SensitiveDataMasker sensitiveDataMasker;

    public SafetyAdvisor(
            PromptInjectionDetector injectionDetector,
            SensitiveDataMasker sensitiveDataMasker) {
        this.injectionDetector = injectionDetector;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String userText = request.prompt().getUserMessage().getText();
        if (isInjection(userText)) {
            return blockedResponse(request, "prompt_injection", FAILURE_RESPONSE);
        }
        if (sensitiveDataMasker.containsSensitiveData(userText)) {
            return blockedResponse(request, "personal_data", PERSONAL_DATA_RESPONSE);
        }
        return chain.nextCall(request);
    }

    public boolean isInjection(String input) {
        return injectionDetector.containsInjection(input);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    static ChatClientResponse blockedResponse(ChatClientRequest request, String reason, String message) {
        String userId = String.valueOf(request.context().getOrDefault(
                AuditAdvisor.AUTHENTICATED_USER_ID, "anonymous"));
        audit.warn("event=PROMPT_BLOCKED user={} reason={}", userId, reason);
        Map<String, Object> responseContext = new java.util.HashMap<>(request.context());
        responseContext.put(BLOCKED, true);
        responseContext.put("safety_reason", reason);
        return ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage(message))))
                        .build())
                .context(responseContext)
                .build();
    }
}
