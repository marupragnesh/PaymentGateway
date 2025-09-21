package com.example.payment.service;

import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.PaymentStats;
import com.example.payment.dto.RefundRequest;
import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.exception.PaymentException;
import com.example.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EmailService emailService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.currency}")
    private String razorpayCurrency;

    // Create payment order with enhanced error handling
    public PaymentResponse createPaymentOrder(PaymentRequest request) {
        logger.info("Creating payment order for amount: {} {}", request.getAmount(), request.getCurrency());

        try {
            // Validate input
            if (request.getAmount() < 1.00) {
                return new PaymentResponse(false, "Minimum amount is ₹1.00");
            }

            if (request.getAmount() > 999999.99) {
                return new PaymentResponse(false, "Maximum amount is ₹999,999.99");
            }

            // Initialize Razorpay client
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            // Convert amount to paise (Razorpay uses smallest currency unit)
            int amountInPaise = (int) Math.round(request.getAmount() * 100);

            // Create order request
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", razorpayCurrency);
            orderRequest.put("receipt", "receipt_" + System.currentTimeMillis());
            orderRequest.put("payment_capture", 1);
            
            // Add notes for reference
            JSONObject notes = new JSONObject();
            notes.put("customerEmail", request.getCustomerEmail());
            notes.put("description", request.getDescription());
            orderRequest.put("notes", notes);

            // Create order
            Order order = razorpayClient.orders.create(orderRequest);

            // Create payment record in database
            Payment payment = new Payment(
                order.get("id"),
                order.get("id"),
                request.getAmount(),
                razorpayCurrency,
                PaymentStatus.PENDING,
                request.getCustomerEmail(),
                request.getDescription()
            );

            // Set payment method type
            payment.setPaymentMethodType("razorpay");
            
            // We'll update card details after payment is completed via webhook

            // Save payment to database
            payment = paymentRepository.save(payment);
            logger.info("Payment record created with ID: {}", payment.getId());

            // Create response with Razorpay order details
            PaymentResponse response = new PaymentResponse();
            response.setSuccess(true);
            response.setPaymentId(order.get("id"));
            response.setOrderId(order.get("id"));
            response.setStatus("created");
            response.setAmount(request.getAmount());
            response.setCurrency(razorpayCurrency);
            
            // Add Razorpay-specific fields
            response.setKeyId(razorpayKeyId);
            
            return response;

        } catch (RazorpayException e) {
            logger.error("Razorpay error: {}", e.getMessage(), e);
            return new PaymentResponse(false, "Payment processing error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating payment: {}", e.getMessage(), e);
            return new PaymentResponse(false, "An unexpected error occurred. Please try again.");
        }
    }

    // Handle payment status and send appropriate emails
    private PaymentResponse handlePaymentStatus(String status, Payment payment) {
        logger.info("Payment status: {} for payment: {}", status, payment.getRazorpayPaymentId());

        switch (status) {
            case "requires_action", "requires_source_action" -> {
                // Payment requires additional authentication
                payment.setStatus(PaymentStatus.REQUIRES_ACTION);
                paymentRepository.save(payment);

                return new PaymentResponse(false, "requires_action",
                        payment.getRazorpayPaymentId(), null);
            }
            case "succeeded" -> {
                // Payment succeeded
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setEmailSent(true);
                paymentRepository.save(payment);

                // Send success email asynchronously
                emailService.sendPaymentSuccessEmail(payment);

                return new PaymentResponse(true, "Payment successful!",
                        payment.getRazorpayPaymentId(), null);
            }
            case "processing" -> {
                // Payment is processing
                payment.setStatus(PaymentStatus.PROCESSING);
                paymentRepository.save(payment);

                return new PaymentResponse(true, "Payment is being processed",
                        payment.getRazorpayPaymentId(), null);
            }
            case "failed" -> {
                // Payment failed
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Payment was declined. Please try a different payment method.");
                payment.setEmailSent(true);
                paymentRepository.save(payment);

                // Send failure email asynchronously
                emailService.sendPaymentFailureEmail(payment);

                return new PaymentResponse(false, "Payment failed: Payment was declined");
            }
            default -> {
                // Handle other statuses
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Unknown payment status: " + status);
                paymentRepository.save(payment);

                return new PaymentResponse(false, "Payment failed: Unknown status");
            }
        }
    }

    // Confirm payment that requires additional action
    public PaymentResponse confirmPayment(String razorpayPaymentId) {
        logger.info("Confirming payment: {}", razorpayPaymentId);

        try {
            // Initialize Razorpay client
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            // Find payment in our database
            Optional<Payment> paymentOpt = paymentRepository.findByRazorpayPaymentId(razorpayPaymentId);
            if (paymentOpt.isEmpty()) {
                return new PaymentResponse(false, "Payment record not found");
            }

            Payment payment = paymentOpt.get();

            // Handle the confirmed payment status
            return handlePaymentStatus("succeeded", payment);

        } catch (RazorpayException e) {
            logger.error("Razorpay error confirming payment: {}", e.getMessage(), e);
            return new PaymentResponse(false, "Error confirming payment: " + e.getLocalizedMessage());
        } catch (Exception e) {
            logger.error("Unexpected error confirming payment: {}", e.getMessage(), e);
            return new PaymentResponse(false, "An unexpected error occurred");
        }
    }

    // Process refund from request object
    public Map<String, Object> refundPayment(RefundRequest request) {
        logger.info("Processing refund for payment: {}, amount: {}", 
                request.getRazorpayPaymentId(), request.getRefundAmount());
        
        PaymentResponse response = processRefund(
                request.getRazorpayPaymentId(), 
                request.getRefundAmount());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", response.isSuccess());
        result.put("message", response.getMessage());
        result.put("paymentId", request.getRazorpayPaymentId());
        result.put("refundAmount", request.getRefundAmount());
        
        return result;
    }
    
    // Process refund
    public PaymentResponse processRefund(String razorpayPaymentId, Double refundAmount) {
        logger.info("Processing refund for payment: {}, amount: {}", razorpayPaymentId, refundAmount);

        try {
            // Initialize Razorpay client with API keys
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            // Find payment in database
            Optional<Payment> paymentOpt = paymentRepository.findByRazorpayPaymentId(razorpayPaymentId);
            if (paymentOpt.isEmpty()) {
                return new PaymentResponse(false, "Payment not found");
            }

            Payment payment = paymentOpt.get();

            // Validate refund
            if (!payment.getStatus().equals(PaymentStatus.SUCCESS)) {
                return new PaymentResponse(false, "Only successful payments can be refunded");
            }

            if (payment.getRefunded()) {
                return new PaymentResponse(false, "Payment already refunded");
            }

            double maxRefundAmount = payment.getAmount() - payment.getRefundedAmount();
            if (refundAmount > maxRefundAmount) {
                return new PaymentResponse(false, "Refund amount exceeds available amount");
            }

            // Create refund with Razorpay
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("payment_id", razorpayPaymentId);
            refundRequest.put("amount", (int) (refundAmount * 100));
            refundRequest.put("notes", new JSONObject().put("reason", "REQUESTED_BY_CUSTOMER"));
            
            // Create refund using Razorpay API
            com.razorpay.Refund refund = razorpayClient.payments.refund(razorpayPaymentId, refundRequest);

            // Update payment record
            payment.setRefundedAmount(payment.getRefundedAmount() + refundAmount);
            if (payment.getRefundedAmount().equals(payment.getAmount())) {
                payment.setRefunded(true);
                payment.setStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }

            paymentRepository.save(payment);

            logger.info("Refund processed successfully: {}", refund.getId());
            return new PaymentResponse(true, "Refund processed successfully");

        } catch (RazorpayException e) {
            logger.error("Razorpay error processing refund: {}", e.getMessage(), e);
            return new PaymentResponse(false, "Error processing refund: " + e.getLocalizedMessage());
        } catch (Exception e) {
            logger.error("Unexpected error processing refund: {}", e.getMessage(), e);
            return new PaymentResponse(false, "An unexpected error occurred");
        }
    }

    // Get payment by ID
    public Payment getPaymentById(String paymentId) throws PaymentException {
        logger.info("Fetching payment details for ID: {}", paymentId);
        Optional<Payment> paymentOpt = paymentRepository.findByRazorpayPaymentId(paymentId);
        return paymentOpt.orElseThrow(() -> new PaymentException("Payment not found"));
    }

    // Get payments by customer with pagination
    public Page<Payment> getPaymentsByCustomer(String customerEmail, Pageable pageable) {
        return paymentRepository.findByCustomerEmailOrderByCreatedAtDesc(customerEmail, pageable);
    }
    
    // Alias method for PaymentController compatibility
    public Page<Payment> getPaymentsByCustomerEmail(String customerEmail, Pageable pageable) {
        return getPaymentsByCustomer(customerEmail, pageable);
    }

    // Get all payments with pagination
    public Page<Payment> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable);
    }

    // Get payment statistics
    public PaymentStats getPaymentStatistics() {
        Object[] successStats = paymentRepository.getPaymentStats(PaymentStatus.SUCCESS);
        Object[] failedStats = paymentRepository.getPaymentStats(PaymentStatus.FAILED);

        Long successCount = (Long) successStats[0];
        Double successAmount = (Double) successStats[1];
        Long failedCount = (Long) failedStats[0];

        return new PaymentStats(
                successCount != null ? successCount : 0L,
                successAmount != null ? successAmount : 0.0,
                failedCount != null ? failedCount : 0L,
                paymentRepository.count());
    }
    
    // Alias method for PaymentController compatibility
    public PaymentStats getPaymentStats() {
        return getPaymentStatistics();
    }

    // Send pending emails (for failed email deliveries)
    public void sendPendingEmails() {
        logger.info("Checking for pending emails to send");

        // Find successful payments where email wasn't sent
        List<Payment> pendingSuccessEmails = paymentRepository.findByEmailSentFalseAndStatus(PaymentStatus.SUCCESS);
        for (Payment payment : pendingSuccessEmails) {
            emailService.sendPaymentSuccessEmail(payment);
            payment.setEmailSent(true);
            paymentRepository.save(payment);
        }

        // Find failed payments where email wasn't sent
        List<Payment> pendingFailureEmails = paymentRepository.findByEmailSentFalseAndStatus(PaymentStatus.FAILED);
        for (Payment payment : pendingFailureEmails) {
            emailService.sendPaymentFailureEmail(payment);
            payment.setEmailSent(true);
            paymentRepository.save(payment);
        }

        logger.info("Sent {} success emails and {} failure emails",
                pendingSuccessEmails.size(), pendingFailureEmails.size());
    }
}