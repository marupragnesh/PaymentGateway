package com.example.payment.repository;

import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Find payment by Razorpay payment ID
    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);
    
    // Find payment by Razorpay order ID
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    // Find all payments by customer email with pagination
    Page<Payment> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail, Pageable pageable);

    // Find payments by status
    List<Payment> findByStatus(PaymentStatus status);

    // Find payments where email not sent
    List<Payment> findByEmailSentFalseAndStatus(PaymentStatus status);

    // Find successful payments by date range
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findPaymentsByStatusAndDateRange(
            @Param("status") PaymentStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // Get payment statistics
    @Query("SELECT COUNT(p), SUM(p.amount) FROM Payment p WHERE p.status = :status")
    Object[] getPaymentStats(@Param("status") PaymentStatus status);

    // Find recent payments
    List<Payment> findTop10ByOrderByCreatedAtDesc();

    // Find payments by amount range
    List<Payment> findByAmountBetween(Double minAmount, Double maxAmount);

    // Count payments by status
    long countByStatus(PaymentStatus status);

    // Find refundable payments
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.refunded = false")
    List<Payment> findRefundablePayments(@Param("status") PaymentStatus status);
}