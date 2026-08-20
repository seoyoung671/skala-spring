package com.example.day3.advisor;

import java.time.Duration;
import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Component
public class TokenMeterAdvisor implements CallAdvisor {

    public static final int ORDER = 900;

    private static final Logger ragAudit = LoggerFactory.getLogger("AI_RAG");

    private final Counter promptTokens;
    private final Counter completionTokens;
    private final Timer modelLatency;

    public TokenMeterAdvisor(MeterRegistry registry) {
        this.promptTokens = Counter.builder("ai.tokens")
                .tag("type", "prompt")
                .tag("feature", "chat")
                .description("AI chat prompt tokens")
                .register(registry);
        this.completionTokens = Counter.builder("ai.tokens")
                .tag("type", "completion")
                .tag("feature", "chat")
                .description("AI chat completion tokens")
                .register(registry);
        this.modelLatency = Timer.builder("ai.latency")
                .tag("phase", "model")
                .tag("feature", "chat")
                .description("AI model and tool execution latency")
                .register(registry);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        logRetrievedDocuments(request);
        long started = System.nanoTime();
        try {
            ChatClientResponse response = chain.nextCall(request);
            recordTokens(response);
            return response;
        } finally {
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

    private void logRetrievedDocuments(ChatClientRequest request) {
        Object value = request.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (!(value instanceof List<?> values)) {
            ragAudit.info("event=RAG_RETRIEVAL count=0 documents=[]");
            return;
        }
        List<String> documents = values.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .map(document -> "%s(score=%s)".formatted(
                        document.getMetadata().getOrDefault("source", "unknown"),
                        document.getScore()))
                .toList();
        ragAudit.info("event=RAG_RETRIEVAL count={} documents={}", documents.size(), documents);
    }
}
