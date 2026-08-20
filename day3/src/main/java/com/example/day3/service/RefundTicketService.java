package com.example.day3.service;

import java.util.UUID;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.day3.domain.RefundTicket;
import com.example.day3.repository.OrderRepository;
import com.example.day3.repository.RefundTicketRepository;
import com.example.day3.tool.RefundTicketView;

@Service
public class RefundTicketService {

    private static final Logger audit = LoggerFactory.getLogger("REFUND_AUDIT");
    private static final int MAX_REASON_LENGTH = 500;

    private final OrderRepository orders;
    private final RefundTicketRepository tickets;

    public RefundTicketService(OrderRepository orders, RefundTicketRepository tickets) {
        this.orders = orders;
        this.tickets = tickets;
    }

    @Transactional
    public RefundTicketView requestRefund(String orderId, String reason, String userId) {
        orders.findByIdAndOwnerId(orderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("환불 사유를 입력해 주세요.");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("환불 사유는 500자 이내로 입력해 주세요.");
        }

        RefundTicket ticket = tickets.save(RefundTicket.pending(
                nextTicketNo(), orderId, userId, reason.strip()));
        audit.info("event=REFUND_REQUESTED ticket={} order={} user={} status={}",
                ticket.getTicketNo(), orderId, userId, ticket.getStatus());
        return RefundTicketView.requested(ticket);
    }

    @Transactional
    public RefundTicketView approve(String ticketNo, String adminUserId) {
        RefundTicket ticket = tickets.findById(ticketNo)
                .orElseThrow(() -> new IllegalArgumentException("환불 요청을 찾을 수 없습니다."));
        ticket.approve(adminUserId);
        audit.info("event=REFUND_APPROVED ticket={} order={} admin={} status={}",
                ticket.getTicketNo(), ticket.getOrderId(), adminUserId, ticket.getStatus());
        return RefundTicketView.approved(ticket);
    }

    @Transactional(readOnly = true)
    public List<RefundTicketView> pending() {
        return tickets.findByStatusOrderByRequestedAtAsc(
                        RefundTicket.TicketStatus.PENDING)
                .stream()
                .map(RefundTicketView::pending)
                .toList();
    }

    private String nextTicketNo() {
        return "RF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
