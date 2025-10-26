package com.example.payment.repository;

import com.example.payment.model.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByRazorpayPaymentId(String razorpayPaymentId);
    List<PaymentEntity> findByRazorpayOrderId(String razorpayOrderId);
}