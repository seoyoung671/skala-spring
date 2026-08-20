package com.example.helpdesk.tools;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.ai.chat.model.ToolContext;

/** ToolContext에서 인증 사용자와 Tool 실행 표시를 안전하게 꺼내는 공통 도우미다. */
final class ToolRequestContext {

    static final String USER_ID = "userId";
    static final String EXECUTED = "toolExecuted";

    private ToolRequestContext() {
    }

    /**
     * 서버가 ChatClient 호출 시 넣은 인증 사용자 ID를 꺼낸다.
     * 모델 인자로 userId를 받으면 다른 사용자를 사칭할 수 있으므로 ToolParam으로 노출하지 않는다.
     */
    static String requiredUserId(ToolContext context) {
        Object value = context == null ? null : context.getContext().get(USER_ID);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
        }
        return value.toString();
    }

    /**
     * 현재 응답이 문서가 아닌 Tool 결과를 근거로 했음을 HelpDeskService에 알린다.
     * 이 표시가 있어야 RAG 출처가 없는 주문·티켓 답변도 무근거 응답으로 오인하지 않는다.
     */
    static void markExecuted(ToolContext context) {
        Object value = context == null ? null : context.getContext().get(EXECUTED);
        if (value instanceof AtomicBoolean executed) {
            executed.set(true);
        }
    }
}
