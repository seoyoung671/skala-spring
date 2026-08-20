package com.example.day3.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.day3.domain.RefundTicket;
import com.example.day3.domain.RefundTicket.TicketStatus;

public interface RefundTicketRepository extends JpaRepository<RefundTicket, String> {

    List<RefundTicket> findByStatusOrderByRequestedAtAsc(TicketStatus status);
}
