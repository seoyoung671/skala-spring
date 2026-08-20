package com.example.day3.tool;

import java.util.Arrays;

import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import com.example.day3.security.SensitiveDataMasker;
import com.example.day3.security.ToolCallBudget;

@Aspect
@Component
public class ToolAuditAspect {

    private static final Logger audit = LoggerFactory.getLogger("AI_TOOL_AUDIT");

    private final MeterRegistry meterRegistry;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final ToolCallBudget toolCallBudget;

    public ToolAuditAspect(
            MeterRegistry meterRegistry,
            SensitiveDataMasker sensitiveDataMasker,
            ToolCallBudget toolCallBudget) {
        this.meterRegistry = meterRegistry;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.toolCallBudget = toolCallBudget;
    }

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object auditToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "#" + joinPoint.getSignature().getName();
        String userId = findUserId(joinPoint.getArgs());
        String arguments = visibleArguments(joinPoint.getArgs());
        long started = System.nanoTime();

        try {
            toolCallBudget.consume();
            Object result = joinPoint.proceed();
            recordToolCall(toolName, "ok");
            audit.info("tool={} args={} user={} status=SUCCESS result={} elapsedMs={}",
                    toolName, arguments, userId, sensitiveDataMasker.mask(String.valueOf(result)),
                    (System.nanoTime() - started) / 1_000_000);
            return result;
        } catch (Throwable error) {
            recordToolCall(toolName, "fail");
            audit.warn("tool={} args={} user={} status=FAIL result={} elapsedMs={}",
                    toolName, arguments, userId, sensitiveDataMasker.mask(error.getMessage()),
                    (System.nanoTime() - started) / 1_000_000);
            throw error;
        }
    }

    private String findUserId(Object[] arguments) {
        return Arrays.stream(arguments)
                .filter(ToolContext.class::isInstance)
                .map(ToolContext.class::cast)
                .map(ToolContext::getContext)
                .map(context -> context.get("userId"))
                .filter(value -> value != null && !value.toString().isBlank())
                .map(Object::toString)
                .findFirst()
                .orElse("anonymous");
    }

    private String visibleArguments(Object[] arguments) {
        String raw = Arrays.stream(arguments)
                .filter(argument -> !(argument instanceof ToolContext))
                .map(String::valueOf)
                .toList()
                .toString();
        return sensitiveDataMasker.mask(raw);
    }

    private void recordToolCall(String toolName, String result) {
        meterRegistry.counter(
                        "ai.tool.calls",
                        "tool", toolName,
                        "result", result,
                        "feature", "chat")
                .increment();
    }
}
