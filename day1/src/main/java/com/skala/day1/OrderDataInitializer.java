package com.skala.day1;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.skala.day1.domain.Order;
import com.skala.day1.domain.Order.OrderStatus;
import com.skala.day1.repository.OrderRepository;

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
                new Order("12345", "user1", "무선 이어폰", OrderStatus.SHIPPING,
                        LocalDate.of(2026, 7, 26), LocalDate.of(2026, 7, 30), new BigDecimal("52000")),
                new Order("12346", "user1", "USB-C 케이블", OrderStatus.DELIVERED,
                        LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 18), new BigDecimal("4000")),
                new Order("12347", "user1", "기계식 키보드", OrderStatus.PREPARING,
                        LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 1), new BigDecimal("98000")),
                new Order("99999", "user2", "노트북 스탠드", OrderStatus.PAID,
                        LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2), new BigDecimal("21000"))));
    }
}
