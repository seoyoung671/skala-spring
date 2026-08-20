package com.example.helpdesk.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tool이 실시간으로 조회할 주문 데이터다. */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private String id;

    // 주문 소유자 검증에 반드시 사용하는 인증 사용자 ID다.
    @Column(nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private String item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private LocalDate eta;

    protected Order() {
        // JPA 전용 기본 생성자
    }

    public Order(String id, String ownerId, String item, OrderStatus status, LocalDate eta) {
        this.id = id;
        this.ownerId = ownerId;
        this.item = item;
        this.status = status;
        this.eta = eta;
    }

    public enum OrderStatus {
        PAID("결제완료"), PREPARING("상품준비중"), SHIPPING("배송중"), DELIVERED("배송완료");

        private final String label;

        OrderStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public String getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getItem() {
        return item;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDate getEta() {
        return eta;
    }
}
