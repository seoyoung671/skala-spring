package com.example.day3.tool;

import com.example.day3.domain.RefundTicket;
import com.example.day3.domain.RefundTicket.TicketStatus;

public record RefundTicketView(
        String ticketNo,
        String orderId,
        TicketStatus status,
        String message) {

    public static RefundTicketView requested(RefundTicket ticket) {
        return new RefundTicketView(
                ticket.getTicketNo(),
                ticket.getOrderId(),
                ticket.getStatus(),
                "환불 요청이 접수되었습니다. 티켓 번호는 %s이며, 담당자 승인을 기다리고 있습니다. 승인 후 처리됩니다."
                        .formatted(ticket.getTicketNo()));
    }

    public static RefundTicketView approved(RefundTicket ticket) {
        return new RefundTicketView(
                ticket.getTicketNo(),
                ticket.getOrderId(),
                ticket.getStatus(),
                "담당자가 환불 요청을 승인했습니다.");
    }

    public static RefundTicketView pending(RefundTicket ticket) {
        return new RefundTicketView(
                ticket.getTicketNo(),
                ticket.getOrderId(),
                ticket.getStatus(),
                "담당자 승인을 기다리고 있습니다.");
    }
}
