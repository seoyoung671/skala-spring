package com.example.helpdesk.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 교환·환불 요청을 담당자 승인 전까지 보관하는 티켓이다. */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    private String ticketNo;

    // 어떤 주문에 대한 요청인지 연결하는 키다. Tool은 저장 전에 주문 소유권을 검사한다.
    @Column(nullable = false)
    private String orderId;

    // 모델이 전달한 값이 아니라 ToolContext의 인증 사용자 ID를 저장한다.
    @Column(nullable = false)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketType type;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(nullable = false)
    private Instant requestedAt;

    protected Ticket() {
        // JPA 전용 기본 생성자
    }

    private Ticket(String ticketNo, String orderId, String requestedBy,
            TicketType type, String reason) {
        this.ticketNo = ticketNo;
        this.orderId = orderId;
        this.requestedBy = requestedBy;
        this.type = type;
        this.reason = reason;
        // Tool은 실제 교환·환불을 처리하지 않고 승인 대기 티켓만 만든다.
        this.status = TicketStatus.PENDING;
        this.requestedAt = Instant.now();
    }

    public static Ticket pending(String ticketNo, String orderId, String requestedBy,
            TicketType type, String reason) {
        // 외부에서 APPROVED 티켓을 바로 만들지 못하게 생성 진입점을 PENDING으로 고정한다.
        return new Ticket(ticketNo, orderId, requestedBy, type, reason);
    }

    public enum TicketType {
        EXCHANGE, REFUND
    }

    public enum TicketStatus {
        // Phase 4 Tool이 만들 수 있는 상태는 PENDING뿐이며 APPROVED는 관리자 단계용이다.
        PENDING, APPROVED
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public TicketType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
