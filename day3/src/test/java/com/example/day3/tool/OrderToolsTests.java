package com.example.day3.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.day3.domain.Order;
import com.example.day3.domain.Order.OrderStatus;
import com.example.day3.repository.OrderRepository;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class OrderToolsTests {

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderTools orderTools;

    @Autowired
    MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        orders.save(new Order(
                "12345", "user1", "무선 이어폰", OrderStatus.SHIPPING, LocalDate.of(2026, 8, 24)));
    }

    @Test
    void 본인_주문은_조회할_수_있다() {
        OrderView result = orderTools.getOrder(
                "12345", new ToolContext(Map.of("userId", "user1")));

        assertThat(result.orderId()).isEqualTo("12345");
        assertThat(result.status()).isEqualTo("배송중");
    }

    @Test
    void 다른_사용자의_주문은_조회할_수_없다() {
        assertThatThrownBy(() -> orderTools.getOrder(
                "12345", new ToolContext(Map.of("userId", "user2"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문을 찾을 수 없습니다.");
    }

    @Test
    void 사용자_컨텍스트가_없으면_실패한다() {
        assertThatThrownBy(() -> orderTools.getOrder(
                "12345", new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("인증된 사용자 정보가 없습니다.");
    }

    @Test
    void 도구_호출의_사용자와_결과가_감사_로그에_남는다(CapturedOutput output) {
        orderTools.getOrder("12345", new ToolContext(Map.of("userId", "user1")));

        assertThat(output.getOut())
                .contains("AI_TOOL_AUDIT")
                .contains("tool=OrderTools#getOrder")
                .contains("args=[12345]")
                .contains("user=user1")
                .contains("status=SUCCESS");
    }

    @Test
    void 도구_호출의_성공과_실패가_metric에_각각_기록된다() {
        double successBefore = toolCallCount("ok");
        double failureBefore = toolCallCount("fail");

        orderTools.getOrder("12345", new ToolContext(Map.of("userId", "user1")));
        assertThatThrownBy(() -> orderTools.getOrder(
                "12345", new ToolContext(Map.of("userId", "user2"))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(toolCallCount("ok")).isEqualTo(successBefore + 1);
        assertThat(toolCallCount("fail")).isEqualTo(failureBefore + 1);
    }

    private double toolCallCount(String result) {
        var counter = meterRegistry.find("ai.tool.calls")
                .tags(
                        "tool", "OrderTools#getOrder",
                        "result", result,
                        "feature", "chat")
                .counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void 한_HTTP_요청에서_도구를_네_번_호출하면_상한에서_중단된다() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        try {
            ToolContext context = new ToolContext(Map.of("userId", "user1"));
            orderTools.getOrder("12345", context);
            orderTools.getOrder("12345", context);
            orderTools.getOrder("12345", context);

            assertThatThrownBy(() -> orderTools.getOrder("12345", context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("한 요청에서 호출할 수 있는 도구 횟수를 초과했습니다.");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
