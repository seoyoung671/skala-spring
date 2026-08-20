package com.example.day3.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.example.day3.domain.Order;
import com.example.day3.domain.Order.OrderStatus;
import com.example.day3.repository.OrderRepository;
import com.example.day3.repository.RefundTicketRepository;
import com.example.day3.service.RefundTicketService;
import com.example.day3.tool.RefundTicketView;

@SpringBootTest
@AutoConfigureMockMvc
class AdminRefundControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrderRepository orders;

    @Autowired
    RefundTicketRepository tickets;

    @Autowired
    RefundTicketService refundTicketService;

    private String ticketNo;

    @BeforeEach
    void setUp() {
        tickets.deleteAll();
        orders.deleteAll();
        orders.save(new Order(
                "12345", "user1", "무선 이어폰", OrderStatus.SHIPPING, LocalDate.of(2026, 8, 24)));
        RefundTicketView ticket = refundTicketService.requestRefund("12345", "단순 변심", "user1");
        ticketNo = ticket.ticketNo();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void 일반_사용자는_환불을_승인할_수_없다() throws Exception {
        mockMvc.perform(post("/lab3/admin/tickets/{ticketNo}/approve", ticketNo))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 관리자만_PENDING_환불을_승인할_수_있다() throws Exception {
        mockMvc.perform(post("/lab3/admin/tickets/{ticketNo}/approve", ticketNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketNo").value(ticketNo))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 관리자는_PENDING_환불_목록을_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/lab3/admin/tickets/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketNo").value(ticketNo))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void 일반_사용자는_PENDING_환불_목록을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/lab3/admin/tickets/pending"))
                .andExpect(status().isForbidden());
    }
}
