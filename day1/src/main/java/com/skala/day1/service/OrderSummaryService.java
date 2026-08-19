package com.skala.day1.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skala.day1.domain.Order;
import com.skala.day1.repository.OrderRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@Transactional(readOnly = true)
public class OrderSummaryService {

    private static final Logger log = LoggerFactory.getLogger(OrderSummaryService.class);

    private final OrderRepository orders;
    private final ChatClient summaryChat;
    private final Counter tokenCounter;
    private final boolean apiKeyConfigured;

    public OrderSummaryService(
            OrderRepository orders,
            @Qualifier("summaryChatClient") ChatClient summaryChatClient,
            MeterRegistry meterRegistry,
            @Value("${spring.ai.openai.api-key:not-set}") String apiKey) {
        this.orders = orders;
        this.summaryChat = summaryChatClient;
        this.tokenCounter = Counter.builder("ai.tokens")
                .description("AI 주문 요약에 사용된 누적 토큰")
                .register(meterRegistry);
        this.apiKeyConfigured = !apiKey.isBlank() && !"not-set".equals(apiKey);
    }

    public SummaryResponse summarize(String orderId, String userId) {
        Order order = orders.findByIdAndOwnerId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        String summary = callModelOrFallback(order);
        return new SummaryResponse(order.getId(), summary);
    }

    /**
     * 주문 정보를 AI에게 보내 한 문장 요약을 만든다.
     *
     * AI 요약은 기본 주문 조회에 붙는 부가 기능이다. API 키가 없거나,
     * OpenAI가 429를 반환하거나, 네트워크 타임아웃이 발생했다는 이유로
     * 주문 정보 전체를 보여 주지 못하면 안 된다. 따라서 AI 호출이
     * 가능하면 모델이 만든 요약을 반환하고, 실패하면 주문 데이터로
     * 만든 기본 문장을 반환한다.
     */
    private String callModelOrFallback(Order order) {
        // application.yml은 OPENAI_API_KEY 환경 변수가 없으면 "not-set"을 사용한다.
        // 키가 없는 것을 알면 OpenAI에 불필요한 요청을 보내지 않고
        // 즉시 폴백을 반환한다. 이렇게 하면 401 오류와 불필요한 대기 시간을 피할 수 있다.
        if (!apiKeyConfigured) {
            return fallback(order);
        }

        // 외부 AI API 호출은 인증 실패, 사용량 초과(429), 네트워크 오류,
        // 타임아웃 등으로 실패할 수 있으므로 try-catch 범위 안에서 호출한다.
        try {
            // Step 1의 Lab1AiConfig에서 만든 summaryChatClient를 사용한다.
            // 따라서 아래 호출에도 시스템 프롬프트, temperature 0.0,
            // maxTokens 120 설정이 기본으로 적용된다.
            ChatResponse response = summaryChat.prompt()

                    // user(...) 메시지에는 현재 주문의 실제 데이터를 넣는다.
                    // {id}, {item}, {status}, {eta}는 아래 param(...) 호출이 채울 템플릿 변수다.
                    .user(user -> user.text("""
                                    주문번호 {id} · 상품 {item} · 상태 {status} · 도착예정 {eta}
                                    위 정보를 한 문장으로 요약해 줘.""")

                            // 문자열을 + 로 직접 연결하지 않고 템플릿 변수에 값을 바인딩한다.
                            // 이 방식은 프롬프트의 구조와 주문 데이터를 구분해 코드를 읽기 쉽게 한다.
                            .param("id", order.getId())
                            .param("item", order.getItem())

                            // DB에 저장된 SHIPPING 같은 enum 코드가 아니라
                            // 사람이 읽을 수 있는 "배송중" 문구를 AI에게 전달한다.
                            .param("status", order.getStatus().label())
                            .param("eta", order.getEta()))

                    // 현재 스레드에서 OpenAI API에 요청을 보내고 응답을 기다린다.
                    .call()

                    // content()만 사용하면 AI가 만든 문자열만 얻을 수 있다.
                    // 이 실습은 답변 문자열과 토큰 사용량을 함께 확인해야 하므로
                    // 응답 메타데이터도 포함한 ChatResponse 전체를 받는다.
                    .chatResponse();

            // 정상적인 API 응답이어도 생성 결과가 비어 있을 가능성을 방어한다.
            // 이 경우 NullPointerException을 발생시키지 않고 폴백 문장을 사용한다.
            if (response == null || response.getResult() == null) {
                return fallback(order);
            }

            // OpenAI가 토큰 사용량 메타데이터를 보낸 경우에만 누적 카운터를 증가시킨다.
            // 누적값은 /actuator/metrics/ai.tokens 엔드포인트에서 확인할 수 있다.
            if (response.getMetadata().getUsage() != null) {
                tokenCounter.increment(response.getMetadata().getUsage().getTotalTokens());
            }

            // ChatResponse에서 모델이 실제로 생성한 텍스트만 꺼내 반환한다.
            return response.getResult().getOutput().getText();
        } catch (RuntimeException exception) {
            // 상세 실패 이유는 서버 로그에만 남긴다.
            // 사용자에게 API 키, 내부 예외, 스택 트레이스를 노출하지 않는다.
            log.warn("AI 주문 요약 실패, 주문 정보로 폴백: {}", exception.getMessage());

            // AI 기능이 실패해도 Repository에서 이미 확인한 주문 정보는 보여 준다.
            return fallback(order);
        }
    }

    /**
     * 외부 AI를 호출하지 않고 주문 데이터만으로 안전한 기본 문장을 만든다.
     * AI 문장보다 단순하지만, 상품·상태·도착 예정일을 계속 제공할 수 있다.
     */
    private String fallback(Order order) {
        return "%s · %s · 도착예정 %s"
                .formatted(order.getItem(), order.getStatus().label(), order.getEta());
    }

    public record SummaryResponse(String orderId, String summary) {
    }
}
