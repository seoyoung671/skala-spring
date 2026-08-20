package com.example.day3.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class OrderChatServiceSourcesTests {

    @Test
    void 검색된_문서의_출처를_중복_없이_추출한다() {
        OrderChatService service = new OrderChatService(null, null);
        List<Document> documents = List.of(
                new Document("반품 규정", Map.of("source", "return-policy.md")),
                new Document("반품 배송비", Map.of("source", "return-policy.md")),
                new Document("배송 규정", Map.of("source", "shipping-policy.md")));

        assertThat(service.extractSources(documents))
                .containsExactly("return-policy.md", "shipping-policy.md");
    }
}
