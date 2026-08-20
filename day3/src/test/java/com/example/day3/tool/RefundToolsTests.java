package com.example.day3.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.day3.domain.Order;
import com.example.day3.domain.Order.OrderStatus;
import com.example.day3.domain.RefundTicket.TicketStatus;
import com.example.day3.repository.OrderRepository;
import com.example.day3.repository.RefundTicketRepository;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class RefundToolsTests {

    @Autowired
    OrderRepository orders;

    @Autowired
    RefundTicketRepository tickets;

    @Autowired
    RefundTools refundTools;

    @BeforeEach
    void setUp() {
        tickets.deleteAll();
        orders.deleteAll();
        orders.save(new Order(
                "12345", "user1", "무선 이어폰", OrderStatus.SHIPPING, LocalDate.of(2026, 8, 24)));
    }

    @Test
    void 본인_주문의_환불은_PENDING으로만_접수된다(CapturedOutput output) {
        RefundTicketView result = refundTools.requestRefund(
                "12345", "단순 변심", new ToolContext(Map.of("userId", "user1")));

        assertThat(result.status()).isEqualTo(TicketStatus.PENDING);
        assertThat(result.message()).contains("승인 후 처리됩니다");
        assertThat(result.message())
                .contains(result.ticketNo())
                .contains("승인을 기다리고 있습니다");
        assertThat(tickets.findById(result.ticketNo())).get()
                .extracting(ticket -> ticket.getStatus())
                .isEqualTo(TicketStatus.PENDING);
        assertThat(output.getOut())
                .contains("event=REFUND_REQUESTED")
                .contains("user=user1")
                .contains("status=PENDING");
    }

    @Test
    void 다른_사용자의_주문은_환불_접수도_할_수_없다() {
        assertThatThrownBy(() -> refundTools.requestRefund(
                "12345", "단순 변심", new ToolContext(Map.of("userId", "user2"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문을 찾을 수 없습니다.");
        assertThat(tickets.count()).isZero();
    }

    @Test
    void 도구_인자에_포함된_개인정보는_감사_로그에서_마스킹된다(CapturedOutput output) {
        refundTools.requestRefund(
                "12345", "test@example.com으로 연락", new ToolContext(Map.of("userId", "user1")));

        assertThat(output.getOut())
                .contains("***@***")
                .doesNotContain("test@example.com");
    }
}
