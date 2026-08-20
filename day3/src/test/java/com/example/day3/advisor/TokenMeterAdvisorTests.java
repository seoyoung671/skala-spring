package com.example.day3.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

class TokenMeterAdvisorTests {

    @Test
    void 토큰과_모델_지연시간을_태그와_함께_기록한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TokenMeterAdvisor advisor = new TokenMeterAdvisor(registry);
        ChatClientRequest request = mock(ChatClientRequest.class);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        Usage usage = mock(Usage.class);

        when(request.context()).thenReturn(Map.of());
        when(usage.getPromptTokens()).thenReturn(120);
        when(usage.getCompletionTokens()).thenReturn(30);

        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of())
                .metadata(ChatResponseMetadata.builder().usage(usage).build())
                .build();
        ChatClientResponse response = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(Map.of())
                .build();
        when(chain.nextCall(request)).thenReturn(response);

        advisor.adviseCall(request, chain);

        assertThat(registry.get("ai.tokens")
                .tags("type", "prompt", "feature", "chat")
                .counter().count()).isEqualTo(120);
        assertThat(registry.get("ai.tokens")
                .tags("type", "completion", "feature", "chat")
                .counter().count()).isEqualTo(30);
        assertThat(registry.get("ai.latency")
                .tags("phase", "model", "feature", "chat")
                .timer().count()).isEqualTo(1);
    }
}
