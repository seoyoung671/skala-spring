package com.example.day3.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refund_tickets")
public class RefundTicket {

    @Id
    private String ticketNo;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String requestedBy;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(nullable = false)
    private Instant requestedAt;

    private String approvedBy;

    private Instant approvedAt;

    protected RefundTicket() {
    }

    private RefundTicket(String ticketNo, String orderId, String requestedBy, String reason) {
        this.ticketNo = ticketNo;
        this.orderId = orderId;
        this.requestedBy = requestedBy;
        this.reason = reason;
        this.status = TicketStatus.PENDING;
        this.requestedAt = Instant.now();
    }

    public static RefundTicket pending(String ticketNo, String orderId, String requestedBy, String reason) {
        return new RefundTicket(ticketNo, orderId, requestedBy, reason);
    }

    public void approve(String adminUserId) {
        if (status != TicketStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 환불 요청입니다.");
        }
        this.status = TicketStatus.APPROVED;
        this.approvedBy = adminUserId;
        this.approvedAt = Instant.now();
    }

    public enum TicketStatus {
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

    public String getReason() {
        return reason;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
