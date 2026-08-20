package com.example.day3.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.example.day3.advisor.AuditAdvisor;

@Service
public class OrderChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public OrderChatService(
            @Qualifier("supportChatClient") ChatClient chatClient,
            ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    public ChatResult chat(String question, String userId, String conversationId) {
        String securedConversationId = securedConversationId(userId, conversationId);
        ChatClientResponse response = chatClient.prompt()
                .user(question)
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, securedConversationId)
                        .param(AuditAdvisor.AUTHENTICATED_USER_ID, userId))
                .toolContext(Map.of("userId", userId))
                .call()
                .chatClientResponse();

        String answer = response.chatResponse() == null || response.chatResponse().getResult() == null
                ? ""
                : response.chatResponse().getResult().getOutput().getText();
        return new ChatResult(answer, extractSources(
                response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS)));
    }

    public List<HistoryMessage> history(String userId, String conversationId) {
        return chatMemory.get(securedConversationId(userId, conversationId)).stream()
                .map(this::toHistoryMessage)
                .toList();
    }

    private HistoryMessage toHistoryMessage(Message message) {
        return new HistoryMessage(message.getMessageType().name(), message.getText());
    }

    private String securedConversationId(String userId, String conversationId) {
        return userId + ":" + conversationId;
    }

    List<String> extractSources(Object retrievedDocuments) {
        if (!(retrievedDocuments instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .map(document -> String.valueOf(document.getMetadata().get("source")))
                .filter(source -> !"null".equals(source))
                .distinct()
                .toList();
    }

    public record HistoryMessage(String role, String content) {
    }

    public record ChatResult(String answer, List<String> sources) {
    }
}
