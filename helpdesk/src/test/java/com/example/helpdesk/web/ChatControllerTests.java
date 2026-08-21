package com.example.helpdesk.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.helpdesk.chat.AnswerDto;
import com.example.helpdesk.chat.AnswerDto.Source;
import com.example.helpdesk.chat.HelpDeskService;
import com.example.helpdesk.chat.StreamEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

@WebMvcTest(ChatController.class)
class ChatControllerTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    HelpDeskService service;

    @Test
    @WithMockUser(username = "user1")
    void returnsStructuredAnswerSourcesAndToolUsage() throws Exception {
        Source source = new Source("return-policy.md", "반품 규정", "2026-08-20");
        when(service.ask("반품 규정 알려줘", "skala", "user1", "browser-a"))
                .thenReturn(new AnswerDto("7일 이내입니다.", List.of(source), false));

        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .header("X-Tenant-Id", "skala")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"반품 규정 알려줘","sessionId":"browser-a"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("7일 이내입니다."))
                .andExpect(jsonPath("$.sources[0].document").value("return-policy.md"))
                .andExpect(jsonPath("$.toolUsed").value(false));
    }

    @Test
    @WithMockUser(username = "user1")
    void streamsTokensThenSourcesAsFinalEvent() throws Exception {
        Source source = new Source("return-policy.md", "반품 규정", "2026-08-20");
        when(service.stream("긴 답변", "skala", "user1", "browser-a"))
                .thenReturn(Flux.just(
                        StreamEvent.token("첫 번째 "),
                        StreamEvent.token("두 번째"),
                        StreamEvent.sources(List.of(source), false)));

        MvcResult started = mockMvc.perform(post("/api/chat/stream")
                        .with(csrf())
                        .header("X-Tenant-Id", "skala")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"question":"긴 답변","sessionId":"browser-a"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                // MockMvc는 비동기 SSE 본문의 기본 판독 문자셋을 ISO-8859-1로 잡는다.
                // 실제 SSE writer가 만든 UTF-8 바이트를 UTF-8로 해석해 한글 토큰도 검증한다.
                .andDo(result -> result.getResponse()
                        .setCharacterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("event:token"),
                        org.hamcrest.Matchers.containsString("첫 번째"),
                        org.hamcrest.Matchers.containsString("event:sources"),
                        org.hamcrest.Matchers.containsString("return-policy.md"))));
    }

    @Test
    @WithMockUser(username = "user1")
    void rejectsInvalidSessionIdBeforeCallingModel() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"질문","sessionId":"다른 사용자:session"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
