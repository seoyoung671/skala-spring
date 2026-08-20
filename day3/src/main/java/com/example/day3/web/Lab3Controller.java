package com.example.day3.web;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.day3.service.OrderChatService;

@RestController
@RequestMapping("/lab3")
public class Lab3Controller {

    private final OrderChatService chatService;

    public Lab3Controller(OrderChatService chatService) {
        this.chatService = chatService;
    }

    public record ChatRequest(
            @NotBlank @Size(max = 2000) String question,
            @NotBlank @Size(max = 100)
            @Pattern(regexp = "[A-Za-z0-9_-]+") String conversationId) {
    }

    public record ChatResponse(String answer, List<String> sources) {
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Principal principal) {
        OrderChatService.ChatResult result = chatService.chat(
                request.question(), principal.getName(), request.conversationId());
        return new ChatResponse(result.answer(), result.sources());
    }

    @GetMapping("/chat/history")
    public List<OrderChatService.HistoryMessage> history(
            @RequestParam String conversationId,
            Principal principal) {
        return chatService.history(principal.getName(), conversationId);
    }
}
