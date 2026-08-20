package com.example.day3.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.day3.advisor.AuditAdvisor;
import com.example.day3.advisor.RetrievedDocumentSafetyAdvisor;
import com.example.day3.advisor.SafetyAdvisor;
import com.example.day3.advisor.TokenMeterAdvisor;
import com.example.day3.tool.OrderTools;
import com.example.day3.tool.RefundTools;

@Configuration
public class AiConfig {

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .order(200)
                .build();
    }

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(
            VectorStore vectorStore,
            @Value("${lab3.rag.top-k:4}") int topK,
            @Value("${lab3.rag.similarity-threshold:0.3}") double similarityThreshold) {
        PromptTemplate promptTemplate = new PromptTemplate("""
                {query}

                [정책 근거]
                {question_answer_context}

                규정에 관한 답은 위 정책 근거만 사용하세요.
                개인 주문 조회와 환불 접수는 제공된 도구를 사용하세요.
                정책 근거에 없는 내용은 추측하지 말고 확인할 수 없다고 답하세요.
                """);
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build())
                .promptTemplate(promptTemplate)
                .order(300)
                .build();
    }

    @Bean
    ChatClient supportChatClient(
            ChatClient.Builder builder,
            AuditAdvisor auditAdvisor,
            SafetyAdvisor safetyAdvisor,
            MessageChatMemoryAdvisor memoryAdvisor,
            QuestionAnswerAdvisor questionAnswerAdvisor,
            RetrievedDocumentSafetyAdvisor retrievedDocumentSafetyAdvisor,
            TokenMeterAdvisor tokenMeterAdvisor,
            OrderTools orderTools,
            RefundTools refundTools) {
        return builder
                .defaultSystem("""
                        너는 친절하고 간결한 고객 상담원이다.
                        주문 정보가 필요하면 제공된 주문 조회 도구를 사용한다.
                        환불 요청은 접수만 하고 담당자 승인 전에는 처리됐다고 말하지 않는다.
                        환불 접수 도구가 성공하면 최종 답변에 반환된 티켓 번호와 PENDING 승인 대기 상태를 반드시 포함한다.
                        도구에서 확인되지 않은 주문 정보를 추측하지 않는다.
                        """)
                .defaultOptions(ChatOptions.builder()
                        .temperature(0.0)
                        .maxTokens(1000)
                        .build())
                .defaultAdvisors(
                        auditAdvisor,
                        safetyAdvisor,
                        memoryAdvisor,
                        questionAnswerAdvisor,
                        retrievedDocumentSafetyAdvisor,
                        tokenMeterAdvisor)
                .defaultTools(orderTools, refundTools)
                .build();
    }
}
