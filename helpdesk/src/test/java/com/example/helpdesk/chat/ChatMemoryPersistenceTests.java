package com.example.helpdesk.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ChatMemoryPersistenceTests {

    private static final String CONVERSATION_ID = "skala:user1:browser-a";

    @Autowired
    ChatMemoryRepository repository;

    // 이 테스트는 대화 JDBC 영속화만 확인하므로 외부 PGvector는 사용하지 않는다.
    @MockitoBean
    VectorStore vectorStore;

    @BeforeEach
    void clearConversation() {
        // 테스트 간 대화가 섞이면 메시지 순서 검증이 달라지므로 같은 ID의 기록을 지운다.
        repository.deleteByConversationId(CONVERSATION_ID);
    }

    @Test
    void keepsThreeTurnContextWhenChatMemoryObjectIsRecreated() {
        ChatMemory firstProcessMemory = newMemory(20);
        firstProcessMemory.add(CONVERSATION_ID, List.of(
                new UserMessage("반품 규정이 어떻게 되나요?"),
                new AssistantMessage("단순 변심은 수령 후 7일 이내입니다."),
                new UserMessage("내 주문 12345는 지금 어디예요?"),
                new AssistantMessage("주문 12345는 배송중입니다."),
                new UserMessage("그럼 그건 반품돼요?"),
                new AssistantMessage("앞서 확인한 주문과 반품 규정을 함께 확인하겠습니다.")));

        // 애플리케이션이 재시작되어 메모리 객체가 새로 만들어진 상황을 재현한다.
        ChatMemory recreatedMemory = newMemory(20);

        assertThat(recreatedMemory.get(CONVERSATION_ID))
                .extracting(message -> message.getText())
                .containsExactly(
                        "반품 규정이 어떻게 되나요?",
                        "단순 변심은 수령 후 7일 이내입니다.",
                        "내 주문 12345는 지금 어디예요?",
                        "주문 12345는 배송중입니다.",
                        "그럼 그건 반품돼요?",
                        "앞서 확인한 주문과 반품 규정을 함께 확인하겠습니다.");
    }

    @Test
    void keepsOnlyTheConfiguredRecentMessageWindow() {
        // 여섯 메시지를 넣고 최대 네 개로 제한해 가장 오래된 두 개가 제거되는지 확인한다.
        ChatMemory smallWindow = newMemory(4);
        smallWindow.add(CONVERSATION_ID, List.of(
                new UserMessage("1"), new AssistantMessage("2"),
                new UserMessage("3"), new AssistantMessage("4"),
                new UserMessage("5"), new AssistantMessage("6")));

        assertThat(smallWindow.get(CONVERSATION_ID))
                .extracting(message -> message.getText())
                .containsExactly("3", "4", "5", "6");
    }

    private ChatMemory newMemory(int maxMessages) {
        // 서로 다른 ChatMemory 객체가 같은 JDBC Repository를 공유하도록 구성한다.
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }
}
