package com.example.helpdesk.chat;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 대화 메모리 키를 tenant, 사용자, 브라우저 세션 단위로 중앙 생성한다.
 * 호출부마다 문자열을 조합하지 않게 해 서로 다른 고객의 대화가 섞이는 사고를 막는다.
 */
@Component
public class ConversationIdFactory {

    private static final String SEPARATOR = ":";

    /**
     * 세 격리 단위를 항상 같은 순서로 조합한다.
     * 클라이언트가 완성된 conversationId를 직접 보내게 하지 않아 다른 사용자의
     * 대화 ID를 임의로 선택하는 위험을 줄인다.
     */
    public String create(String tenantId, String userId, String sessionId) {
        return "%s:%s:%s".formatted(
                segment(tenantId, "테넌트 ID"),
                segment(userId, "사용자 ID"),
                segment(sessionId, "세션 ID"));
    }

    private String segment(String value, String fieldName) {
        // 빈 구간이 있으면 서로 다른 테넌트·사용자·세션을 구분할 수 없으므로 거부한다.
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "이(가) 필요합니다.");
        }
        // 앞뒤 공백 차이로 동일 세션에 여러 메모리 키가 생기지 않게 정규화한다.
        String normalized = value.strip();
        // 구분자를 허용하면 서로 다른 세 입력 조합이 같은 ID가 될 수 있다.
        if (normalized.contains(SEPARATOR)) {
            throw new IllegalArgumentException(fieldName + "에는 ':'를 사용할 수 없습니다.");
        }
        return normalized;
    }
}
