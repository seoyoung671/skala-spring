package com.example.helpdesk;

import java.time.LocalDate;
import java.util.List;

import com.example.helpdesk.domain.Order;
import com.example.helpdesk.domain.Order.OrderStatus;
import com.example.helpdesk.repository.OrderRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 로컬 실습에서 주문 Tool을 바로 확인할 수 있도록 예제 주문을 준비한다. */
@Component
public class OrderDataInitializer implements ApplicationRunner {

    private final OrderRepository orders;

    public OrderDataInitializer(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 애플리케이션 재시작이나 영속 DB 사용 시 예제 주문이 중복되지 않게 한다.
        if (orders.count() > 0) {
            return;
        }
        orders.saveAll(List.of(
                new Order("12345", "user1", "무선 이어폰", OrderStatus.SHIPPING,
                        LocalDate.of(2026, 8, 24)),
                new Order("12346", "user1", "USB-C 케이블", OrderStatus.DELIVERED,
                        LocalDate.of(2026, 8, 18)),
                new Order("99999", "user2", "노트북 스탠드", OrderStatus.PAID,
                        LocalDate.of(2026, 8, 27))));
    }
}
