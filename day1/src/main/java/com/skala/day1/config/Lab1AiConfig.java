package com.skala.day1.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Day 1 실습에서 사용할 AI 클라이언트를 만드는 설정 클래스.
 *
 * 주문 요약은 창의적인 글쓰기와 달리 결과가 매번 비슷해야 하고,
 * 주문에 없는 내용을 추측해서도 안 된다. 그래서 공용 ChatClient를
 * 그대로 사용하지 않고, 주문 요약 규칙을 미리 고정한 전용 Bean을 만든다.
 *
 * 나중에 고객 상담이나 광고 문구 작성 기능이 추가된다면,
 * supportChatClient, marketingChatClient처럼 용도별 Bean을
 * 별도로 만들어 각자 다른 프롬프트·온도·토큰 상한을 적용할 수 있다.
 */
@Configuration
public class Lab1AiConfig {

    /**
     * 주문 요약 전용 ChatClient를 Spring Bean으로 등록한다.
     *
     * ChatClient.Builder는 Spring AI 자동 설정이 만들어 주는
     * 공통 생성기다. application.yml에 설정한 OpenAI API 키와
     * 모델 정보가 이 Builder에 연결되어 있다.
     *
     * 메서드 이름이 summaryChatClient이므로 Bean 이름도
     * summaryChatClient가 된다. ChatClient Bean이 여러 개일 때는
     * Service에서 @Qualifier("summaryChatClient")로 이 Bean을 선택한다.
     */
    @Bean
    public ChatClient summaryChatClient(ChatClient.Builder builder) {
        return builder
                // 모든 주문 요약 요청에 기본으로 붙는 시스템 메시지이다.
                // 사용자가 보내는 주문 정보보다 상위 규칙으로 작용하여
                // AI의 역할, 출력 형식, 안전 규칙을 일관되게 유지한다.
                .defaultSystem("""
                        너는 이커머스 주문 상담 도우미다.
                        주어진 주문 정보만 사용해 한국어 한 문장으로 요약한다.
                        추측하지 않는다. 정보가 부족하면 "정보가 부족합니다"라고 답한다.""")

                // 개별 호출마다 옵션을 다시 지정하지 않도록
                // 주문 요약에 사용할 기본 모델 옵션을 Bean에 고정한다.
                .defaultOptions(ChatOptions.builder()

                        // 온도는 답변의 무작위성·다양성을 조절한다.
                        // 요약은 창의성보다 일관성이 중요하므로 0.0을 사용한다.
                        // 따라서 같은 주문을 반복해도 결과가 가능한 비슷하게 나온다.
                        .temperature(0.0)

                        // 모델이 생성할 수 있는 최대 출력 토큰 수다.
                        // 한 문장 요약에는 긴 출력이 필요하지 않으므로 120으로 제한해
                        // 불필요한 답변 길이, 응답 시간, API 사용 비용을 줄인다.
                        .maxTokens(120)

                        // 위에서 설정한 온도와 토큰 상한으로 ChatOptions를 완성한다.
                        .build())

                // 시스템 프롬프트와 기본 옵션을 포함한 ChatClient를 최종 생성한다.
                .build();
    }
}
