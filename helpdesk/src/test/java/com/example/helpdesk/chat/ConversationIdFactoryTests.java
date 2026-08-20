package com.example.helpdesk.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConversationIdFactoryTests {

    private final ConversationIdFactory factory = new ConversationIdFactory();

    @Test
    void combinesTenantUserAndSessionInOneStableRule() {
        // ID 조합 순서가 호출부마다 달라지지 않도록 결과 형식을 고정한다.
        assertThat(factory.create("skala", "user1", "browser-a"))
                .isEqualTo("skala:user1:browser-a");
    }

    @Test
    void differentUsersAndSessionsNeverShareTheSameConversationId() {
        // 사용자 또는 세션 중 하나만 달라도 완전히 다른 메모리 공간이어야 한다.
        String first = factory.create("skala", "user1", "browser-a");
        String otherUser = factory.create("skala", "user2", "browser-a");
        String otherSession = factory.create("skala", "user1", "browser-b");

        assertThat(first).isNotEqualTo(otherUser).isNotEqualTo(otherSession);
    }

    @Test
    void rejectsSeparatorThatCouldCreateAmbiguousConversationId() {
        // "a:b" + "c"와 "a" + "b:c"처럼 같은 문자열이 되는 충돌을 막는다.
        assertThatThrownBy(() -> factory.create("skala:user1", "admin", "browser-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("':'");
    }
}
