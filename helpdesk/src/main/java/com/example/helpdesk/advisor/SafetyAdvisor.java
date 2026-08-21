package com.example.helpdesk.advisor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.helpdesk.security.PromptInjectionDetector;
import com.example.helpdesk.security.SensitiveDataMasker;
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

/** Day 3 방식으로 직접 인젝션과 개인정보 패턴을 모델 호출 전에 차단한다. */
@Component
public class SafetyAdvisor implements CallAdvisor {

    public static final String BLOCKED = "safety_blocked";
    public static final int ORDER = 100;

    private static final Logger audit = LoggerFactory.getLogger("AI_SAFETY");
    private static final String INJECTION_RESPONSE =
            "안전 정책에 따라 해당 요청은 처리할 수 없습니다.";
    private static final String PERSONAL_DATA_RESPONSE =
            "개인정보가 포함된 요청은 처리할 수 없습니다. 개인정보를 제거해 주세요.";

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
        if (injectionDetector.containsInjection(userText)) {
            return blockedResponse(request, "prompt_injection", INJECTION_RESPONSE);
        }
        if (sensitiveDataMasker.containsSensitiveData(userText)) {
            return blockedResponse(request, "personal_data", PERSONAL_DATA_RESPONSE);
        }
        return chain.nextCall(request);
    }

    static ChatClientResponse blockedResponse(
            ChatClientRequest request, String reason, String message) {
        Object userId = request.context().getOrDefault(
                AuditAdvisor.AUTHENTICATED_USER_ID, "anonymous");
        audit.warn("event=PROMPT_BLOCKED user={} reason={}", userId, reason);
        Map<String, Object> context = new HashMap<>(request.context());
        // 예외를 던지는 대신 정상 응답 형태를 만들어 반환하면 Controller 계약은 유지하면서
        // chain.nextCall()을 생략해 이후 Advisor와 실제 모델 호출을 즉시 중단할 수 있다.
        context.put(BLOCKED, true);
        context.put("safety_reason", reason);
        return ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage(message))))
                        .build())
                .context(context)
                .build();
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
