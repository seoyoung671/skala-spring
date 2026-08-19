package com.skala.day1.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skala.day1.service.OrderSummaryService;
import com.skala.day1.service.OrderSummaryService.SummaryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 주문 요약 HTTP 요청을 받는 웹 계층이다.
 *
 * Controller는 URL·파라미터를 받고 Service에 전달한 다음,
 * Service가 반환한 결과를 JSON 응답으로 내보낸다.
 * 주문 조회, 소유자 확인, AI 호출 같은 업무 로직은 직접 처리하지 않는다.
 *
 * 이렇게 역할을 나누면 OpenAI 대신 다른 모델을 사용하거나
 * 프롬프트를 바꿔도 Controller는 수정할 필요가 없다.
 */
// 이 클래스가 일반 화면 Controller가 아니라
// JSON 데이터를 반환하는 REST API Controller임을 Spring에게 알린다.
@RestController

// 이 Controller의 모든 API에 공통으로 붙는 URL 앞부분이다.
// 아래 메서드의 /{orderId}/summary와 합쳐서 최종 경로가 된다.
@RequestMapping("/lab1/orders")

// Swagger UI에서 이 Controller의 API를 묶어 보여 줄 그룹 이름이다.
@Tag(name = "Day1 실습 · 주문 요약")
public class OrderSummaryController {

    // Controller는 ChatClient나 Repository를 직접 사용하지 않고 Service만 알고 있다.
    // 즉 이 클래스의 역할은 "요청을 받아 Service에 전달하는 것"까지다.
    private final OrderSummaryService service;

    // 생성자 주입 방식으로 OrderSummaryService Bean을 받는다.
    // 생성자가 하나뿐이므로 @Autowired를 작성하지 않아도 Spring이 자동 주입한다.
    // final 필드라서 Controller가 생성된 후 다른 Service로 바뀐지 않는다.
    public OrderSummaryController(OrderSummaryService service) {
        this.service = service;
    }

    // HTTP GET 요청을 처리한다.
    // 클래스의 /lab1/orders와 합쳐진 최종 주소는
    // GET /lab1/orders/{orderId}/summary 이다.
    // 예: GET /lab1/orders/12345/summary?userId=user1
    @GetMapping("/{orderId}/summary")

    // Swagger UI에 표시될 API 이름과 상세 설명이다.
    // 이 API는 실제 AI 모델을 호출할 수 있으므로 비용 발생 가능성도 문서에 알린다.
    @Operation(summary = "주문 한 문장 요약",
               description = "본인 주문만 요약된다. 모델을 호출하므로 비용이 발생할 수 있다.")

    // 상태 코드별 응답을 Swagger 문서에 정의한다.
    // 200: 주문을 찾았고 요약 결과를 반환한 경우
    // 404: 주문이 없거나 해당 사용자 소유의 주문이 아닌 경우
    // 503: DB 또는 서버의 예상하지 못한 오류로 요청을 처리하지 못한 경우
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요약 성공",
                         content = @Content(schema = @Schema(implementation = SummaryResponseExample.class))),
            @ApiResponse(responseCode = "404", description = "없는 주문이거나 남의 주문", content = @Content),
            @ApiResponse(responseCode = "503", description = "일시적인 서비스 오류", content = @Content)
    })
    public SummaryResponse summary(
            // URL 경로의 {orderId}를 String orderId 매개변수로 받는다.
            // Swagger Try it out에서는 12345가 예시로 표시된다.
            @Parameter(description = "주문번호", example = "12345")
            @PathVariable String orderId,

            // ?userId=user1 형태의 쿼리 파라미터를 받는다.
            // 실제 서비스에서는 요청 파라미터 대신 로그인 인증 정보에서 사용자 ID를 꺼낸다.
            @Parameter(description = "조회 주체", example = "user1")
            @RequestParam String userId) {

        // Controller에서 주문 조회나 AI 호출을 직접 하지 않는다.
        // 두 입력값을 Service에 전달하고 결과를 그대로 반환한다.
        // @RestController 때문에 SummaryResponse는 자동으로 JSON으로 변환된다.
        return service.summarize(orderId, userId);
    }

    // Swagger 문서에서 200 응답의 필드 구조와 예시값을 보여 주는 문서용 DTO다.
    // 실제 API 응답과 동일하게 orderId와 summary 두 필드로 구성된다.
    @Schema(name = "SummaryResponseExample")
    record SummaryResponseExample(
            // Swagger에 주문번호 예시로 12345를 표시한다.
            @Schema(example = "12345") String orderId,

            // Swagger에 AI가 생성할 수 있는 한 문장 요약 예시를 표시한다.
            @Schema(example = "무선 이어폰이 배송 중이며 7월 30일 도착 예정입니다.") String summary) {
    }
}
