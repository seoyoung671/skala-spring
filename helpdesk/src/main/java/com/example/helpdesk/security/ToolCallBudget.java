package com.example.helpdesk.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 한 HTTP 요청에서 모델이 Tool을 과도하게 반복 호출하지 못하게 제한한다. */
@Component
public class ToolCallBudget {

    private static final String CALL_COUNT_ATTRIBUTE =
            ToolCallBudget.class.getName() + ".callCount";

    private final int maxCallsPerRequest;

    public ToolCallBudget(
            @Value("${helpdesk.tools.max-calls-per-request:3}") int maxCallsPerRequest) {
        if (maxCallsPerRequest < 1) {
            throw new IllegalArgumentException("도구 호출 상한은 1 이상이어야 합니다.");
        }
        this.maxCallsPerRequest = maxCallsPerRequest;
    }

    public void consume() {
        // 호출 횟수를 singleton Bean 필드에 두면 여러 사용자의 요청이 섞인다.
        // 현재 HttpServletRequest의 attribute에 저장해 요청마다 독립된 예산을 갖게 한다.
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes)) {
            // HTTP 요청 밖에서 수행되는 단위 테스트나 내부 호출에는 요청별 상태가 없으므로 건너뛴다.
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        Object value = request.getAttribute(CALL_COUNT_ATTRIBUTE);
        int currentCount = value instanceof Integer count ? count : 0;
        if (currentCount >= maxCallsPerRequest) {
            throw new IllegalStateException("한 요청에서 호출할 수 있는 도구 횟수를 초과했습니다.");
        }
        request.setAttribute(CALL_COUNT_ATTRIBUTE, currentCount + 1);
    }
}
