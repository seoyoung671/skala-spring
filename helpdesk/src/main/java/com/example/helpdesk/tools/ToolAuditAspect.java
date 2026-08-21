package com.example.helpdesk.tools;

import java.util.Arrays;

import com.example.helpdesk.security.SensitiveDataMasker;
import com.example.helpdesk.security.ToolCallBudget;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

/** 모든 Tool 호출의 성공·실패와 지연시간을 기록하고 호출 횟수를 제한한다. */
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
        // 개별 Tool마다 계측 코드를 반복하지 않고 @Tool 메서드의 공통 경계에서
        // 호출 제한, 감사 로그, 성공·실패 메트릭을 한 번에 적용한다.
        String toolName = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "#" + joinPoint.getSignature().getName();
        String userId = findUserId(joinPoint.getArgs());
        String arguments = visibleArguments(joinPoint.getArgs());
        long started = System.nanoTime();
        try {
            toolCallBudget.consume();
            Object result = joinPoint.proceed();
            record(toolName, "ok");
            audit.info("tool={} args={} user={} status=SUCCESS result={} elapsedMs={}",
                    toolName, arguments, userId,
                    sensitiveDataMasker.mask(String.valueOf(result)), elapsedMs(started));
            return result;
        } catch (Throwable error) {
            record(toolName, "fail");
            audit.warn("tool={} args={} user={} status=FAIL result={} elapsedMs={}",
                    toolName, arguments, userId,
                    sensitiveDataMasker.mask(error.getMessage()), elapsedMs(started));
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

    private void record(String toolName, String result) {
        meterRegistry.counter("ai.tool.calls",
                "tool", toolName, "result", result, "feature", "helpdesk").increment();
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
