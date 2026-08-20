package com.example.helpdesk.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code application.properties}의 {@code helpdesk.*} 값을 타입 안전하게 받는다.
 *
 * record를 사용해 설정 객체를 불변으로 유지하고 Bean Validation으로 잘못된
 * 운영 설정을 애플리케이션 시작 시점에 발견한다.
 */
@Validated
@ConfigurationProperties(prefix = "helpdesk")
public record HelpDeskProperties(@Valid Rag rag, @Valid Memory memory, @Valid Ingest ingest) {

    /** RAG 검색 범위와 검색 결과 채택 기준이다. */
    public record Rag(
            // 너무 많은 청크가 프롬프트에 들어가 비용과 잡음이 증가하지 않도록 제한한다.
            @Min(1) @Max(20) int topK,
            // Spring AI 유사도 점수 범위에 맞춰 0.0~1.0만 허용한다.
            @DecimalMin("0.0") @DecimalMax("1.0") double threshold) {
    }

    /** 한 대화에서 보존할 최근 메시지 개수다. */
    public record Memory(@Min(1) @Max(100) int max) {
    }

    /** 문서 분할 크기와 검색 결과 미리보기 길이다. */
    public record Ingest(
            @Min(100) @Max(4000) int chunkSize,
            @Min(50) @Max(2000) int minChunkSizeChars,
            @Min(40) @Max(1000) int previewLength) {
    }
}
