package com.skala.day1.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skala.day1.domain.Order;

public interface OrderRepository extends JpaRepository<Order, String> {

    /** 주문 번호와 소유자를 DB 쿼리에서 함께 검사한다. */
    Optional<Order> findByIdAndOwnerId(String id, String ownerId);
}
