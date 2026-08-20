package com.example.helpdesk.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import com.example.helpdesk.domain.Order;
import com.example.helpdesk.domain.Order.OrderStatus;
import com.example.helpdesk.domain.Ticket;
import com.example.helpdesk.domain.Ticket.TicketStatus;
import com.example.helpdesk.domain.Ticket.TicketType;
import com.example.helpdesk.repository.OrderRepository;
import com.example.helpdesk.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;

class TicketToolsTests {

    private OrderRepository orders;
    private TicketRepository tickets;
    private TicketTools ticketTools;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        tickets = mock(TicketRepository.class);
        ticketTools = new TicketTools(orders, tickets);
    }

    @Test
    void createsOnlyPendingTicketForOwnedOrder() {
        // 쓰기 Tool의 결과 문구뿐 아니라 Repository에 전달된 실제 엔티티 상태도 확인한다.
        Order order = new Order(
                "12345", "user1", "무선 이어폰", OrderStatus.DELIVERED,
                LocalDate.of(2026, 8, 18));
        when(orders.findByIdAndOwnerId("12345", "user1")).thenReturn(Optional.of(order));
        when(tickets.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String result = ticketTools.createTicket(
                "12345", "refund", "단순 변심",
                new ToolContext(Map.of("userId", "user1")));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(captor.capture());
        Ticket saved = captor.getValue();
        assertThat(saved.getRequestedBy()).isEqualTo("user1");
        assertThat(saved.getType()).isEqualTo(TicketType.REFUND);
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.PENDING);
        assertThat(result)
                .contains(saved.getTicketNo())
                .contains("PENDING")
                .contains("승인 후 처리");
    }

    @Test
    void refusesTicketForAnotherUsersOrder() {
        // 소유권 검증 실패 뒤에는 어떠한 티켓도 저장되면 안 된다.
        when(orders.findByIdAndOwnerId("12345", "user2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketTools.createTicket(
                "12345", "REFUND", "단순 변심",
                new ToolContext(Map.of("userId", "user2"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 주문을 찾을 수 없습니다.");
        verify(tickets, never()).save(any());
    }

    @Test
    void acceptsOnlyExchangeOrRefundType() {
        // 모델이 허용하지 않은 작업 종류를 만들어도 서버 enum 검증에서 차단한다.
        Order order = new Order(
                "12345", "user1", "무선 이어폰", OrderStatus.DELIVERED,
                LocalDate.of(2026, 8, 18));
        when(orders.findByIdAndOwnerId("12345", "user1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> ticketTools.createTicket(
                "12345", "CANCEL", "취소 요청",
                new ToolContext(Map.of("userId", "user1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXCHANGE 또는 REFUND");
        verify(tickets, never()).save(any());
    }
}
