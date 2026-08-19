package com.skala.day1.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.skala.day1.service.OrderNotFoundException;

/**
 * Day 1 주문 요약 API에서 발생한 예외을 HTTP 응답으로 바꾸는 클래스다.
 *
 * Service는 문제가 발생하면 HTTP 상태 코드를 직접 고르지 않고 예외을 던진다.
 * 이 클래스가 그 예외을 받아 404, 503 같은 HTTP 상태 코드와
 * 사용자에게 보여 줄 JSON 응답으로 변환한다.
 *
 * 예외 처리를 한 곳에 모으면 Controller마다 try-catch를 반복하지 않아도 되고,
 * 모든 API가 같은 형식의 오류 응답을 반환할 수 있다.
 */
// 여러 REST Controller에서 발생하는 예외을 공통으로 처리하겠다는 의미다.
// 이 설정 덕분에 OrderSummaryController에는 예외 처리 코드가 필요 없다.
@RestControllerAdvice
public class Lab1ExceptionHandler {

    // 예외의 상세 내용을 서버 로그에 기록하기 위한 로거다.
    // 사용자 응답에는 스택 트레이스를 넣지 않고, 서버 로그에만 남긴다.
    private static final Logger log = LoggerFactory.getLogger(Lab1ExceptionHandler.class);

    // OrderNotFoundException이 발생했을 때만 이 메서드가 실행된다.
    // Service에서 주문번호와 사용자 ID로 주문을 찾지 못하면 이 예외을 던진다.
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(OrderNotFoundException exception) {
        // 주문이 정말 없는지, 다른 사용자의 주문인지를 구분해 알려 주지 않는다.
        // 구분해 주면 공격자가 특정 주문번호의 존재 여부를 알 수 있기 때문이다.
        // 따라서 두 경우 모두 같은 404와 같은 메시지를 반환한다.
        return ResponseEntity.status(404)
                .body(new ErrorResponse("주문을 찾을 수 없습니다.", null));
    }

    // 위에서 별도로 처리하지 않은 모든 예상 못 한 예외을 처리한다.
    // 예: DB 연결 문제, 코드 버그, 데이터 변환 실패 등
    // AI 호출 실패는 OrderSummaryService에서 폴백으로 처리하므로 보통 여기까지 오지 않는다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        // 오류 한 건을 식별할 수 있도록 무작위 UUID의 앞 8자리를 추적 ID로 사용한다.
        // 사용자가 고객센터에 이 값을 알려 주면 운영자가 로그에서 같은 ID를 찾을 수 있다.
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        // 로그에는 추적 ID와 예외 객체 전체를 함께 남긴다.
        // exception을 마지막 인자로 넘기면 메시지뿐 아니라 스택 트레이스까지 기록된다.
        log.error("[{}] 주문 요약 실패", traceId, exception);

        // 예상하지 못한 일시적 장애라고 판단해 HTTP 503을 반환한다.
        // 사용자에게는 복잡한 내부 예외 대신 재시도 가능한 안전한 메시지를 보여 준다.
        // API 키, SQL, 서버 경로, 스택 트레이스 같은 내부 정보는 응답에 포함하지 않는다.
        return ResponseEntity.status(503).body(new ErrorResponse(
                "요약을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.", traceId));
    }

    // 오류 응답의 JSON 구조를 정의한다.
    // message: 사용자에게 보여 줄 안전한 안내 문구
    // traceId: 서버 로그와 오류 응답을 연결할 추적 ID
    // 일반적인 404는 추적이 필요하지 않으므로 traceId가 null이고, 예상 못 한 503에는 값이 들어간다.
    public record ErrorResponse(String message, String traceId) {
    }
}
