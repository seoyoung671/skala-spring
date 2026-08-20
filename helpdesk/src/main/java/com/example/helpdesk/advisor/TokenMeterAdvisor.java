package com.example.helpdesk.advisor;

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.stereotype.Component;

/**
 * 모델 응답 메타데이터의 토큰 사용량과 호출 지연을 Micrometer에 기록한다.
 * 생성된 지표는 Actuator를 통해 조회하고 이후 비용·성능 분석에 사용할 수 있다.
 */
@Component
public class TokenMeterAdvisor implements CallAdvisor {

    // 모델 호출에 가까운 위치에서 순수 AI 처리 구간을 측정한다.
    public static final int ORDER = 900;

    private final Counter promptTokens;
    private final Counter completionTokens;
    private final Timer modelLatency;

    public TokenMeterAdvisor(MeterRegistry registry) {
        // 같은 지표 이름에 type 태그를 붙여 입력과 출력 토큰을 분리한다.
        // feature 태그는 다른 AI 기능의 사용량과 HelpDesk 사용량을 구분한다.
        this.promptTokens = registry.counter("ai.tokens", "type", "prompt", "feature", "helpdesk");
        this.completionTokens = registry.counter("ai.tokens", "type", "completion", "feature", "helpdesk");
        this.modelLatency = registry.timer("ai.latency", "phase", "model", "feature", "helpdesk");
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long started = System.nanoTime();
        try {
            ChatClientResponse response = chain.nextCall(request);
            recordTokens(response);
            return response;
        } finally {
            // 모델 호출이 실패해도 지연시간은 운영상 중요한 정보이므로 항상 기록한다.
            modelLatency.record(Duration.ofNanos(System.nanoTime() - started));
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

    private void recordTokens(ChatClientResponse response) {
        // 폴백 응답이나 일부 공급자는 usage 메타데이터를 제공하지 않을 수 있다.
        if (response.chatResponse() == null || response.chatResponse().getMetadata().getUsage() == null) {
            return;
        }
        var usage = response.chatResponse().getMetadata().getUsage();
        if (usage.getPromptTokens() != null) {
            promptTokens.increment(usage.getPromptTokens());
        }
        if (usage.getCompletionTokens() != null) {
            completionTokens.increment(usage.getCompletionTokens());
        }
    }
}
