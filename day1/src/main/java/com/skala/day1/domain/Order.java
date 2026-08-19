package com.skala.day1.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private String id;

    @Column(nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private String item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private LocalDate orderedAt;
    private LocalDate eta;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    protected Order() {
        // JPA 전용 기본 생성자
    }

    public Order(String id, String ownerId, String item, OrderStatus status,
                 LocalDate orderedAt, LocalDate eta, BigDecimal cost) {
        this.id = id;
        this.ownerId = ownerId;
        this.item = item;
        this.status = status;
        this.orderedAt = orderedAt;
        this.eta = eta;
        this.cost = cost;
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

    public LocalDate getOrderedAt() {
        return orderedAt;
    }

    public LocalDate getEta() {
        return eta;
    }

    public BigDecimal getCost() {
        return cost;
    }
}
