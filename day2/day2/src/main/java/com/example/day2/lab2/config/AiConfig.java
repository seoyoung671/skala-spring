package com.example.day2.lab2.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    ChatClient policyChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        너는 사내 규정 안내 도우미다.
                        - 제공된 근거 안의 내용만으로 답하세요.
                        - 근거에서 답을 찾을 수 없으면 "제공된 문서에서 확인되지 않습니다"라고 답하세요.
                        - 추측하거나 일반 상식으로 내용을 채우지 마세요.
                        - 존댓말로 간결하게 답하세요.""")
                .defaultOptions(ChatOptions.builder().temperature(0.0).build())
                .build();
    }

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
