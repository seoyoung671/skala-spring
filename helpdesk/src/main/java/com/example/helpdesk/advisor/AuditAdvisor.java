package com.example.helpdesk.advisor;

import com.example.helpdesk.security.SensitiveDataMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.stereotype.Component;

/**
 * AI 요청의 시작, 정상 응답, 실패와 전체 소요시간을 감사 로그에 남긴다.
 *
 * 현재는 개인정보 유출을 피하기 위해 질문과 답변 원문을 기록하지 않는다.
 * 사용자·대화 식별자와 마스킹은 감사 기능을 다루는 후속 Phase에서 확장한다.
 */
@Component
public class AuditAdvisor implements CallAdvisor {

    public static final String AUTHENTICATED_USER_ID = "authenticated_user_id";
    // 가장 바깥에서 전체 Advisor 체인과 모델 호출 시간을 관찰한다.
    public static final int ORDER = 0;
    // 일반 애플리케이션 로그와 분리해 운영 환경에서 별도 보관할 수 있는 로거다.
    private static final Logger audit = LoggerFactory.getLogger("AI_CHAT_AUDIT");

    private final SensitiveDataMasker sensitiveDataMasker;

    public AuditAdvisor(SensitiveDataMasker sensitiveDataMasker) {
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long started = System.nanoTime();
        String userId = String.valueOf(request.context().getOrDefault(
                AUTHENTICATED_USER_ID, "anonymous"));
        audit.info("event=CHAT_REQUEST user={} question={}", userId,
                sensitiveDataMasker.mask(abbreviate(
                        request.prompt().getUserMessage().getText(), 300)));
        try {
            // 반드시 다음 Advisor를 호출해야 최종적으로 모델까지 요청이 전달된다.
            ChatClientResponse response = chain.nextCall(request);
            audit.info("event=CHAT_RESPONSE user={} answer={} elapsedMs={}",
                    userId, sensitiveDataMasker.mask(abbreviate(answer(response), 300)),
                    elapsedMillis(started));
            return response;
        } catch (RuntimeException error) {
            // 내부 메시지나 요청 원문 대신 예외 종류만 기록해 민감정보 노출을 줄인다.
            audit.warn("event=CHAT_FAILED errorType={} elapsedMs={}",
                    error.getClass().getSimpleName(), elapsedMillis(started));
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

    private long elapsedMillis(long started) {
        // nanoTime은 벽시계 변경의 영향을 받지 않아 경과시간 측정에 적합하다.
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String answer(ChatClientResponse response) {
        if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return "";
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return String.valueOf(value);
        }
        return value.substring(0, maxLength) + "...";
    }
}
