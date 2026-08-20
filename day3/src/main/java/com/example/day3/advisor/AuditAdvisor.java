package com.example.day3.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.stereotype.Component;

import com.example.day3.security.SensitiveDataMasker;

@Component
public class AuditAdvisor implements CallAdvisor {

    public static final String AUTHENTICATED_USER_ID = "authenticated_user_id";
    public static final int ORDER = 0;

    private static final Logger audit = LoggerFactory.getLogger("AI_CHAT_AUDIT");

    private final SensitiveDataMasker sensitiveDataMasker;

    public AuditAdvisor(SensitiveDataMasker sensitiveDataMasker) {
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String userId = String.valueOf(request.context().getOrDefault(AUTHENTICATED_USER_ID, "anonymous"));
        String conversationId = String.valueOf(request.context().getOrDefault(
                org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, "unknown"));
        String question = sensitiveDataMasker.mask(abbreviate(request.prompt().getUserMessage().getText(), 300));
        long started = System.nanoTime();

        audit.info("event=CHAT_REQUEST user={} conversation={} question={}",
                userId, conversationId, question);
        try {
            ChatClientResponse response = chain.nextCall(request);
            boolean blocked = Boolean.TRUE.equals(response.context().get(SafetyAdvisor.BLOCKED));
            audit.info("event=CHAT_RESPONSE user={} conversation={} blocked={} answer={} elapsedMs={}",
                    userId, conversationId, blocked,
                    sensitiveDataMasker.mask(abbreviate(answer(response), 300)), elapsedMs(started));
            return response;
        } catch (RuntimeException error) {
            audit.warn("event=CHAT_FAIL user={} conversation={} error={} elapsedMs={}",
                    userId, conversationId, sensitiveDataMasker.mask(error.getMessage()), elapsedMs(started));
            throw error;
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    private String answer(ChatClientResponse response) {
        if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return "";
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return String.valueOf(value);
        }
        return value.substring(0, maxLength) + "...";
    }

}
