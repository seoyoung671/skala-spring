package com.example.helpdesk.web;

import java.security.Principal;
import java.time.Duration;

import com.example.helpdesk.chat.AnswerDto;
import com.example.helpdesk.chat.HelpDeskService;
import com.example.helpdesk.chat.StreamEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** 구조화된 REST 응답과 SSE 스트리밍을 제공하는 채팅 API다. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(60);

    private final HelpDeskService service;

    public ChatController(HelpDeskService service) {
        this.service = service;
    }

    /** 질문과 브라우저 상담 세션을 표현하는 공통 요청 DTO다. */
    public record AskRequest(
            @NotBlank @Size(max = 2000) String question,
            @NotBlank @Size(max = 100)
            @Pattern(regexp = "[A-Za-z0-9_-]+") String sessionId) {
    }

    /** 답변 전체가 만들어진 뒤 답변·출처·Tool 사용 여부를 JSON으로 반환한다. */
    @PostMapping
    public AnswerDto ask(
            @Valid @RequestBody AskRequest request,
            @RequestHeader(name = "X-Tenant-Id", defaultValue = "skala") String tenantId,
            Principal principal) {
        return service.ask(
                request.question(), tenantId, principal.getName(), request.sessionId());
    }

    /**
     * 생성 중인 텍스트를 token 이벤트로 보내고 마지막에 sources 이벤트를 보낸다.
     * 클라이언트는 token을 이어 붙여 답변을 표시하고 sources로 출처 UI를 완성한다.
     */
    // SSE 표준 데이터는 UTF-8로 전송한다. charset을 명시하면 브라우저뿐 아니라
    // MockMvc 같은 테스트 클라이언트도 한글 토큰을 같은 문자셋으로 해석한다.
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<Object>> stream(
            @Valid @RequestBody AskRequest request,
            @RequestHeader(name = "X-Tenant-Id", defaultValue = "skala") String tenantId,
            Principal principal) {
        return service.stream(
                        request.question(), tenantId, principal.getName(), request.sessionId())
                .map(this::toServerSentEvent)
                .timeout(STREAM_TIMEOUT);
    }

    private ServerSentEvent<Object> toServerSentEvent(StreamEvent event) {
        return ServerSentEvent.builder(event.data())
                .event(event.type())
                .build();
    }
}
