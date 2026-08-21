package com.example.helpdesk.chat;

import java.util.List;

/**
 * 모델의 답변과 그 답변에 사용된 실제 문서 출처를 함께 반환한다.
 *
 * answer는 사용자에게 표시할 최종 문장이고 sources는 Advisor가 실제로 검색한
 * 문서에서 만든 목록이다. toolUsed는 실시간 Tool 사용 여부를 화면에 알려 준다.
 * 근거가 없을 때 sources는 null 대신 빈 목록을 사용한다.
 */
public record AnswerDto(String answer, List<Source> sources, boolean toolUsed) {

    /**
     * document는 원본 파일명, title은 화면 표시명, version은 인제스트 버전이다.
     * record의 값 동등성을 이용해 같은 문서의 여러 검색 청크를 쉽게 중복 제거한다.
     */
    public record Source(String document, String title, String version) {
    }
}
