package com.example.helpdesk.chat;

import java.util.List;

import com.example.helpdesk.chat.AnswerDto.Source;

/** HelpDeskService가 Controller에 전달하는 SSE용 내부 이벤트다. */
public record StreamEvent(String type, Object data) {

    public static StreamEvent token(String text) {
        return new StreamEvent("token", text);
    }

    public static StreamEvent sources(List<Source> sources, boolean toolUsed) {
        return new StreamEvent("sources", new StreamSummary(sources, toolUsed));
    }

    /** 스트림 마지막에 출처와 Tool 사용 여부를 함께 전달한다. */
    public record StreamSummary(List<Source> sources, boolean toolUsed) {
    }
}
