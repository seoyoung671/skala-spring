package com.example.helpdesk.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import com.example.helpdesk.eval.GoldenSet.Flow;
import org.junit.jupiter.api.Test;

/** 검증 시나리오가 다섯 품질 축을 빠짐없이 표현하는지 확인한다. */
class GoldenSetTests {

    @Test
    void coversAllFiveHelpDeskFlows() {
        var scenarios = GoldenSet.scenarios();

        // 새 기능을 추가하다 대표 시나리오를 실수로 지우면 즉시 발견한다.
        assertThat(scenarios).hasSize(5);
        assertThat(scenarios)
                .extracting(GoldenSet.Scenario::flow)
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Flow.class));
        assertThat(scenarios)
                .extracting(GoldenSet.Scenario::id)
                .doesNotHaveDuplicates();
    }

    @Test
    void multiTurnScenarioKeepsPolicyOrderAndFollowUpInOneSession() {
        var scenario = scenario(Flow.MEMORY_AND_TOOL);

        // 세 질문이 동일 Scenario와 sessionId 아래 있어야 대명사 "그건"을 검증할 수 있다.
        assertThat(scenario.turns())
                .extracting(GoldenSet.Turn::message)
                .containsExactly(
                        "반품 규정 알려줘",
                        "내 주문 12345는?",
                        "그럼 그건 반품돼요?");
        assertThat(scenario.expectation().sourceRequired()).isTrue();
        assertThat(scenario.expectation().toolRequired()).isTrue();
    }

    @Test
    void writeScenarioRequiresPendingApprovalSignals() {
        var expectation = scenario(Flow.APPROVAL_GATE).expectation();

        // 단순 성공 문구가 아니라 승인 전에는 처리되지 않았다는 신호가 필요하다.
        assertThat(expectation.toolRequired()).isTrue();
        assertThat(expectation.pendingApprovalRequired()).isTrue();
        assertThat(expectation.expectedFragments())
                .contains("티켓", "PENDING", "승인 후 처리");
    }

    @Test
    void safetyAndFallbackUseNonDisclosureAndNoEvidenceMessages() {
        assertThat(scenario(Flow.SAFETY).expectation().expectedFragments())
                .contains("해당 주문을 찾을 수 없습니다");
        assertThat(scenario(Flow.FALLBACK).expectation().expectedFragments())
                .contains("제공된 문서에서 확인되지 않습니다");
    }

    private GoldenSet.Scenario scenario(Flow flow) {
        return GoldenSet.scenarios().stream()
                .filter(candidate -> candidate.flow() == flow)
                .findFirst()
                .orElseThrow();
    }
}
