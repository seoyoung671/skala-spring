package com.example.day3.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AdvisorOrderTests {

    @Autowired
    AuditAdvisor auditAdvisor;

    @Autowired
    SafetyAdvisor safetyAdvisor;

    @Autowired
    MessageChatMemoryAdvisor memoryAdvisor;

    @Autowired
    QuestionAnswerAdvisor questionAnswerAdvisor;

    @Autowired
    RetrievedDocumentSafetyAdvisor retrievedDocumentSafetyAdvisor;

    @Autowired
    TokenMeterAdvisor tokenMeterAdvisor;

    @Test
    void 차단은_메모리와_RAG보다_먼저_실행된다() {
        assertThat(auditAdvisor.getOrder()).isEqualTo(0);
        assertThat(safetyAdvisor.getOrder()).isEqualTo(100);
        assertThat(memoryAdvisor.getOrder()).isEqualTo(200);
        assertThat(questionAnswerAdvisor.getOrder()).isEqualTo(300);
        assertThat(retrievedDocumentSafetyAdvisor.getOrder()).isEqualTo(350);
        assertThat(tokenMeterAdvisor.getOrder()).isEqualTo(900);

        assertThat(safetyAdvisor.getOrder()).isLessThan(memoryAdvisor.getOrder());
        assertThat(safetyAdvisor.getOrder()).isLessThan(questionAnswerAdvisor.getOrder());
        assertThat(questionAnswerAdvisor.getOrder()).isLessThan(retrievedDocumentSafetyAdvisor.getOrder());
        assertThat(retrievedDocumentSafetyAdvisor.getOrder()).isLessThan(tokenMeterAdvisor.getOrder());
    }
}
