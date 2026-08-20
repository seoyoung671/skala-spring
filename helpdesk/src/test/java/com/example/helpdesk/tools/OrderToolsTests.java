package com.example.helpdesk.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import com.example.helpdesk.domain.Order;
import com.example.helpdesk.domain.Order.OrderStatus;
import com.example.helpdesk.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class OrderToolsTests {

    private OrderRepository orders;
    private OrderTools orderTools;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        orderTools = new OrderTools(orders);
    }

    @Test
    void looksUpOrderWithAuthenticatedOwnerId() {
        Order order = new Order(
                "12345", "user1", "무선 이어폰", OrderStatus.SHIPPING,
                LocalDate.of(2026, 8, 24));
        when(orders.findByIdAndOwnerId("12345", "user1")).thenReturn(Optional.of(order));

        String result = orderTools.orderStatus(
                "12345", new ToolContext(Map.of("userId", "user1")));

        assertThat(result)
                .contains("12345", "무선 이어폰", "배송중", "2026-08-24");
        // 모델 인자만 사용하지 않고 인증 사용자 ID까지 DB 조건에 포함했는지 확인한다.
        verify(orders).findByIdAndOwnerId("12345", "user1");
    }

    @Test
    void doesNotExposeWhetherAnotherUsersOrderExists() {
        // 실제 주문 존재 여부와 관계없이 소유자 조건에서 조회되지 않으면 같은 문구를 반환한다.
        when(orders.findByIdAndOwnerId("12345", "user2")).thenReturn(Optional.empty());

        String result = orderTools.orderStatus(
                "12345", new ToolContext(Map.of("userId", "user2")));

        assertThat(result).isEqualTo("해당 주문을 찾을 수 없습니다.");
    }

    @Test
    void rejectsToolCallWithoutAuthenticatedUser() {
        // 모델이 주문번호를 알고 있어도 서버 인증 정보가 없으면 Repository를 호출할 수 없다.
        assertThatThrownBy(() -> orderTools.orderStatus(
                "12345", new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("인증된 사용자 정보가 없습니다.");
    }
}
