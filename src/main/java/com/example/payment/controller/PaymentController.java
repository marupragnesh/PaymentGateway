package com.example.payment.controller;

import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.RefundRequest;
import com.example.payment.dto.PaymentStats;
import com.example.payment.entity.Payment;
import com.example.payment.exception.PaymentException;
import com.example.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = {"http://localhost:3000", "https://your-domain.com"})
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private PaymentService paymentService;
    
    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @PostMapping("/create")
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) throws PaymentException {
        logger.info("Creating Razorpay payment for customer: {}", request.getCustomerEmail());
        PaymentResponse response = paymentService.createPaymentIntent(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentResponse> verifyPayment(@RequestBody Map<String, String> request) throws PaymentException {
        String paymentId = request.get("razorpay_payment_id");
        String orderId = request.get("razorpay_order_id");
        String signature = request.get("razorpay_signature");
        
        if (paymentId == null || paymentId.isEmpty()) {
            throw new PaymentException("Razorpay payment ID is required");
        }

        logger.info("Verifying Razorpay payment: {}", paymentId);
        PaymentResponse response = paymentService.confirmPayment(paymentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refund")
    public ResponseEntity<Map<String, Object>> refundPayment(@Valid @RequestBody RefundRequest request) throws PaymentException {
        logger.info("Processing refund for Razorpay payment: {}", request.getPaymentIntentId());
        Map<String, Object> response = paymentService.refundPayment(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> getPaymentById(@PathVariable String paymentId) throws PaymentException {
        logger.info("Fetching Razorpay payment details for ID: {}", paymentId);
        Payment payment = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(payment);
    }

    @GetMapping
    public ResponseEntity<Page<Payment>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("Fetching all Razorpay payments, page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<Payment> payments = paymentService.getAllPayments(pageable);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/customer/{email}")
    public ResponseEntity<Page<Payment>> getPaymentsByCustomer(
            @PathVariable String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("Fetching Razorpay payments for customer: {}", email);
        Pageable pageable = PageRequest.of(page, size);
        Page<Payment> payments = paymentService.getPaymentsByCustomerEmail(email, pageable);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/stats")
    public ResponseEntity<PaymentStats> getPaymentStats() {
        logger.info("Fetching Razorpay payment statistics");
        PaymentStats stats = paymentService.getPaymentStats();
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/key")
    public ResponseEntity<Map<String, String>> getRazorpayKey() {
        Map<String, String> response = new HashMap<>();
        response.put("key_id", razorpayKeyId);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<Map<String, String>> handlePaymentException(PaymentException ex) {
        logger.error("Razorpay payment error: {}", ex.getMessage(), ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", java.time.Instant.now().toString());
        health.put("service", "payment-gateway");
        return ResponseEntity.ok(health);
    }

    @PostMapping("/admin/send-pending-emails")
    public ResponseEntity<Map<String, String>> sendPendingEmails() {
        paymentService.sendPendingEmails();
        return ResponseEntity.ok(Map.of("message", "Pending emails sent successfully"));
    }
}