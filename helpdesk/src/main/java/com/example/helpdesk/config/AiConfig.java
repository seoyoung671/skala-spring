package com.example.helpdesk.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.helpdesk.advisor.AuditAdvisor;
import com.example.helpdesk.advisor.TokenMeterAdvisor;
import com.example.helpdesk.tools.OrderTools;
import com.example.helpdesk.tools.TicketTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * HelpDesk가 사용하는 AI 기반 객체를 한곳에서 생성한다.
 *
 * Service는 모델이나 Advisor를 직접 조립하지 않고 이 설정이 제공하는
 * {@code helpDeskClient}만 주입받는다. 이렇게 하면 모든 상담 요청에
 * 동일한 시스템 프롬프트, 안전 규칙, 메모리, RAG와 관측 정책이 적용된다.
 */
@Configuration
// helpdesk.* 설정을 HelpDeskProperties record에 바인딩한다.
@EnableConfigurationProperties(HelpDeskProperties.class)
public class AiConfig {

    // Advisor의 숫자가 작을수록 요청을 더 바깥쪽에서 먼저 감싼다.
    // 감사 → 안전 → 메모리 → RAG → 계측 순서를 명시적으로 유지한다.
    private static final int SAFETY_ORDER = 100;
    private static final int MEMORY_ORDER = 200;
    private static final int RAG_ORDER = 300;

    /**
     * 최근 대화를 정해진 개수만큼 유지하고 JDBC 저장소에 영속화하는 메모리다.
     * 최대 메시지 수를 설정 파일에서 읽어 코드 수정 없이 조절할 수 있다.
     */
    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository, HelpDeskProperties properties) {
        return MessageWindowChatMemory.builder()
                // PostgreSQL 운영 DB와 H2 테스트 DB 모두 같은 Repository 추상화를 사용한다.
                .chatMemoryRepository(repository)
                // 전체 대화를 무제한으로 프롬프트에 넣지 않고 최근 메시지만 유지해
                // 토큰 사용량과 모델 지연시간을 통제한다.
                .maxMessages(properties.memory().max())
                .build();
    }

    /**
     * HelpDesk 전용 ChatClient를 생성한다.
     *
     * defaultAdvisors로 등록한 Advisor는 이 클라이언트의 모든 호출에 자동 적용된다.
     * 각 요청에서 같은 Advisor를 반복해서 지정하지 않으므로 정책 누락을 방지한다.
     */
    @Bean
    ChatClient helpDeskClient(
            ChatClient.Builder builder,
            // PGvector starter의 자동 설정이 생성하는 VectorStore 구현을 주입받는다.
            VectorStore vectorStore,
            ChatMemory chatMemory,
            HelpDeskProperties properties,
            AuditAdvisor auditAdvisor,
            TokenMeterAdvisor tokenMeterAdvisor,
            OrderTools orderTools,
            TicketTools ticketTools,
            Resource systemPrompt) throws IOException {

        // 민감정보가 포함된 질문은 모델이나 RAG로 전달되기 전에 즉시 차단한다.
        // 현재는 키워드 기반의 1차 방어이며 정규식 탐지는 후속 Phase에서 확장한다.
        SafeGuardAdvisor safetyAdvisor = SafeGuardAdvisor.builder()
                .sensitiveWords(List.of("주민등록번호", "카드번호"))
                .failureResponse("민감정보가 포함된 요청은 처리할 수 없습니다. 해당 정보를 제거해 주세요.")
                .order(SAFETY_ORDER)
                .build();

        // conversationId에 연결된 이전 메시지를 현재 프롬프트에 추가하고
        // 이번 사용자 질문과 모델 응답을 다시 메모리에 저장한다.
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .order(MEMORY_ORDER)
                .build();

        // 질문과 유사한 정책 청크를 검색해 모델의 근거 컨텍스트로 제공한다.
        // 검색 개수와 임계값은 운영 중 조정할 수 있도록 외부 설정에서 읽는다.
        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(properties.rag().topK())
                        .similarityThreshold(properties.rag().threshold())
                        .build())
                .order(RAG_ORDER)
                .build();

        return builder
                // 프롬프트 정책을 Java 코드와 분리해 별도 검토·수정할 수 있게 한다.
                .defaultSystem(systemPrompt.getContentAsString(StandardCharsets.UTF_8))
                .defaultAdvisors(
                        auditAdvisor,
                        safetyAdvisor,
                        memoryAdvisor,
                        questionAnswerAdvisor,
                        tokenMeterAdvisor)
                // 문서 검색으로 알 수 없는 실시간 주문과 티켓 업무를 모델이 호출한다.
                .defaultTools(orderTools, ticketTools)
                .build();
    }

    /** 클래스패스에 있는 HelpDesk 시스템 프롬프트를 Resource Bean으로 노출한다. */
    @Bean
    Resource systemPrompt(org.springframework.core.io.ResourceLoader resourceLoader) {
        return resourceLoader.getResource("classpath:/prompts/system.st");
    }
}
