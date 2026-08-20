package com.example.day3.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.day3.domain.Order;

public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByIdAndOwnerId(String id, String ownerId);
}
