package com.example.helpdesk.tools;

import java.util.Locale;
import java.util.UUID;

import com.example.helpdesk.domain.Ticket;
import com.example.helpdesk.domain.Ticket.TicketType;
import com.example.helpdesk.repository.OrderRepository;
import com.example.helpdesk.repository.TicketRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 교환·환불 요청을 실제 처리하지 않고 승인 대기 티켓으로 접수하는 Tool이다. */
@Component
public class TicketTools {

    private static final int MAX_REASON_LENGTH = 500;

    private final OrderRepository orders;
    private final TicketRepository tickets;

    public TicketTools(OrderRepository orders, TicketRepository tickets) {
        this.orders = orders;
        this.tickets = tickets;
    }

    @Tool(description = """
            교환 또는 환불 티켓을 접수한다. 실제 처리는 담당자 승인 후 진행된다.
            사용자가 명시적으로 교환·환불을 요청하고 주문번호와 사유를 제공했을 때만 사용한다.
            """)
    @Transactional
    public String createTicket(
            @ToolParam(description = "교환·환불을 요청할 주문번호") String orderId,
            @ToolParam(description = "EXCHANGE 또는 REFUND") String type,
            @ToolParam(description = "사용자가 말한 요청 사유") String reason,
            ToolContext context) {
        String userId = ToolRequestContext.requiredUserId(context);
        ToolRequestContext.markExecuted(context);

        // 쓰기 Tool에서도 먼저 orderId + userId로 소유권을 검증한다.
        orders.findByIdAndOwnerId(orderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문을 찾을 수 없습니다."));

        TicketType ticketType = parseType(type);
        String normalizedReason = validateReason(reason);
        Ticket ticket = tickets.save(Ticket.pending(
                nextTicketNo(), orderId, userId, ticketType, normalizedReason));

        // 실제 처리가 끝났다고 표현하지 않고 티켓 번호와 승인 대기 상태를 명시한다.
        return "티켓 %s를 접수했습니다. 현재 PENDING 상태이며 담당자 승인 후 처리됩니다."
                .formatted(ticket.getTicketNo());
    }

    private TicketType parseType(String type) {
        // 모델이 소문자로 전달해도 enum으로 정규화하되 허용하지 않은 작업은 거부한다.
        try {
            return TicketType.valueOf(type == null ? "" : type.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("티켓 종류는 EXCHANGE 또는 REFUND여야 합니다.");
        }
    }

    private String validateReason(String reason) {
        // 빈 사유나 DB 컬럼 제한을 넘는 입력은 티켓 저장 전에 차단한다.
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("교환·환불 사유를 입력해 주세요.");
        }
        String normalized = reason.strip();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("교환·환불 사유는 500자 이내여야 합니다.");
        }
        return normalized;
    }

    private String nextTicketNo() {
        // 사용자에게 안내하고 이후 승인 단계에서 조회할 수 있는 외부 티켓 번호다.
        return "TK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
