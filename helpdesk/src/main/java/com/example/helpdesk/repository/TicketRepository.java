package com.example.helpdesk.repository;

import com.example.helpdesk.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

/** 승인 대기 교환·환불 티켓 저장소다. */
public interface TicketRepository extends JpaRepository<Ticket, String> {
}
