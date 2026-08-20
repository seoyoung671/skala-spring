package com.example.day3.tool;

import java.time.LocalDate;

import com.example.day3.domain.Order;

public record OrderView(String orderId, String item, String status, LocalDate eta) {

    public static OrderView from(Order order) {
        return new OrderView(
                order.getId(),
                order.getItem(),
                order.getStatus().label(),
                order.getEta());
    }
}
