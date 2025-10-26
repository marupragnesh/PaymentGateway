package com.example.payment.controller;

import com.example.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "*") // For testing; restrict in production
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private PaymentService paymentService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Payment service is running");
        return ResponseEntity.ok(response);
    }

    /**
     * Create Razorpay order
     * POST /api/payment/create-order
     * Body: { "amount": 50000, "currency": "INR", "customerName": "John", "customerEmail": "john@example.com", "customerPhone": "9999999999" }
     */
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> requestData) {
        try {
            // Extract and validate request data
            Long amount = Long.parseLong(requestData.get("amount").toString());
            String currency = requestData.getOrDefault("currency", "INR").toString();
            String customerName = requestData.getOrDefault("customerName", "Guest").toString();
            String customerEmail = requestData.getOrDefault("customerEmail", "").toString();
            String customerPhone = requestData.getOrDefault("customerPhone", "").toString();

            logger.info("Creating order for amount: {} {}", amount, currency);

            // Create order via service
            Map<String, Object> orderData = paymentService.createOrder(amount, currency, customerName, customerEmail, customerPhone);

            // Add key ID for frontend
            orderData.put("keyId", razorpayKeyId);

            return ResponseEntity.ok(orderData);

        } catch (Exception e) {
            logger.error("Error creating order: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Verify payment after Razorpay checkout
     * POST /api/payment/verify
     * Body: { "razorpay_order_id": "...", "razorpay_payment_id": "...", "razorpay_signature": "..." }
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> requestData) {
        try {
            String razorpayOrderId = requestData.get("razorpay_order_id");
            String razorpayPaymentId = requestData.get("razorpay_payment_id");
            String razorpaySignature = requestData.get("razorpay_signature");

            logger.info("Verifying payment: Order ID: {}, Payment ID: {}", razorpayOrderId, razorpayPaymentId);

            // Verify payment via service
            Map<String, Object> verificationResult = paymentService.verifyPayment(razorpayOrderId, razorpayPaymentId, razorpaySignature);

            return ResponseEntity.ok(verificationResult);

        } catch (Exception e) {
            logger.error("Error verifying payment: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("status", "failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * Razorpay webhook endpoint
     * POST /api/payment/webhook
     * Headers: X-Razorpay-Signature
     * Body: Raw webhook payload
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody String payload,
                                           @RequestHeader("X-Razorpay-Signature") String signature) {
        try {
            logger.info("Received webhook with signature: {}", signature);

            // Process webhook via service
            paymentService.processWebhook(payload, signature);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing webhook: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * Get order status
     * GET /api/payment/status/{orderId}
     */
    @GetMapping("/status/{orderId}")
    public ResponseEntity<?> getOrderStatus(@PathVariable String orderId) {
        try {
            logger.info("Fetching status for order: {}", orderId);

            Map<String, Object> status = paymentService.getOrderStatus(orderId);
            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("Error fetching order status: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
}