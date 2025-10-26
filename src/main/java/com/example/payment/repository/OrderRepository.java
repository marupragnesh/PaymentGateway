package com.example.payment.repository;

import com.example.payment.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByRazorpayOrderId(String razorpayOrderId);
    Optional<OrderEntity> findByReceipt(String receipt);
}