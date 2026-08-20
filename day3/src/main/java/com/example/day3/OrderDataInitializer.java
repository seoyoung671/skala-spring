package com.example.day3;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.day3.domain.Order;
import com.example.day3.domain.Order.OrderStatus;
import com.example.day3.repository.OrderRepository;

@Component
public class OrderDataInitializer implements ApplicationRunner {

    private final OrderRepository orders;

    public OrderDataInitializer(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (orders.count() > 0) {
            return;
        }
        orders.saveAll(List.of(
                new Order("12345", "user1", "무선 이어폰", OrderStatus.SHIPPING, LocalDate.of(2026, 8, 24)),
                new Order("12346", "user1", "USB-C 케이블", OrderStatus.DELIVERED, LocalDate.of(2026, 8, 18)),
                new Order("99999", "user2", "노트북 스탠드", OrderStatus.PAID, LocalDate.of(2026, 8, 27))));
    }
}
