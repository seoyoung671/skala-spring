package com.example.day3.web;

import java.security.Principal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.day3.service.RefundTicketService;
import com.example.day3.tool.RefundTicketView;

@RestController
@RequestMapping("/lab3/admin/tickets")
public class AdminRefundController {

    private final RefundTicketService refundTickets;

    public AdminRefundController(RefundTicketService refundTickets) {
        this.refundTickets = refundTickets;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RefundTicketView> pending() {
        return refundTickets.pending();
    }

    @PostMapping("/{ticketNo}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public RefundTicketView approve(@PathVariable String ticketNo, Principal principal) {
        return refundTickets.approve(ticketNo, principal.getName());
    }
}
