package com.example.helpdesk.tools;

import com.example.helpdesk.repository.OrderRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** 문서로 알 수 없는 실시간 주문 상태를 조회하는 Tool이다. */
@Component
public class OrderTools {

    private final OrderRepository orders;

    public OrderTools(OrderRepository orders) {
        this.orders = orders;
    }

    @Tool(description = """
            주문번호로 현재 배송 상태와 예상 도착일을 조회한다.
            사용자가 자신의 주문 상태나 배송 예정일을 물을 때 사용한다.
            """)
    public String orderStatus(
            @ToolParam(description = "조회할 주문번호") String orderId,
            ToolContext context) {
        String userId = ToolRequestContext.requiredUserId(context);
        ToolRequestContext.markExecuted(context);

        // 모델이 선택한 orderId만으로 조회하면 다른 사용자의 주문이 노출될 수 있다.
        // 인증 사용자 ID를 같은 DB 조건에 넣어 Tool 경계에서 소유권을 검증한다.
        return orders.findByIdAndOwnerId(orderId, userId)
                .map(order -> "주문 %s · 상품 %s · 상태 %s · 예상도착 %s".formatted(
                        order.getId(), order.getItem(), order.getStatus().label(), order.getEta()))
                // 주문 존재 여부와 타인 소유 여부를 같은 응답으로 처리해 정보 노출을 막는다.
                .orElse("해당 주문을 찾을 수 없습니다.");
    }
}
