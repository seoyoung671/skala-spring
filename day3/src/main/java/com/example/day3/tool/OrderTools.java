package com.example.day3.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.day3.repository.OrderRepository;

@Component
public class OrderTools {

    private final OrderRepository orders;

    public OrderTools(OrderRepository orders) {
        this.orders = orders;
    }

    @Tool(description = """
            주문 상태를 조회한다. 사용자가 주문번호를 말하거나
            '내 주문', '배송 언제'처럼 물으면 이 도구를 쓴다.
            사용자 본인의 주문만 조회할 수 있다.
            """)
    public OrderView getOrder(
            @ToolParam(description = "조회할 주문번호. 예: 12345") String orderId,
            ToolContext context) {

        String userId = currentUserId(context);
        return orders.findByIdAndOwnerId(orderId, userId)
                .map(OrderView::from)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    private String currentUserId(ToolContext context) {
        Object userId = context == null ? null : context.getContext().get("userId");
        if (userId == null || userId.toString().isBlank()) {
            throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
        }
        return userId.toString();
    }
}
