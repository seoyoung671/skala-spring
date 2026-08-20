package com.example.day3.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.day3.service.RefundTicketService;

@Component
public class RefundTools {

    private final RefundTicketService refundTickets;

    public RefundTools(RefundTicketService refundTickets) {
        this.refundTickets = refundTickets;
    }

    @Tool(description = """
            환불 요청을 접수한다. 실제 환불은 즉시 처리되지 않고 담당자 승인 후 처리된다.
            사용자가 명시적으로 환불을 요청하고 주문번호와 사유를 말했을 때만 이 도구를 쓴다.
            접수에 성공하면 반환된 티켓 번호와 승인 대기 상태를 사용자에게 반드시 안내한다.
            """)
    public RefundTicketView requestRefund(
            @ToolParam(description = "환불을 요청할 주문번호. 예: 12345") String orderId,
            @ToolParam(description = "사용자가 말한 환불 사유") String reason,
            ToolContext context) {
        return refundTickets.requestRefund(orderId, reason, currentUserId(context));
    }

    private String currentUserId(ToolContext context) {
        Object userId = context == null ? null : context.getContext().get("userId");
        if (userId == null || userId.toString().isBlank()) {
            throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
        }
        return userId.toString();
    }
}
