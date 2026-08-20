package com.example.helpdesk.repository;

import java.util.Optional;

import com.example.helpdesk.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/** 주문번호와 소유자를 한 쿼리에서 함께 확인하는 주문 저장소다. */
public interface OrderRepository extends JpaRepository<Order, String> {

    // findById() 후 ownerId를 비교하는 대신 DB 조회 조건 자체에 소유자를 포함한다.
    Optional<Order> findByIdAndOwnerId(String id, String ownerId);
}
