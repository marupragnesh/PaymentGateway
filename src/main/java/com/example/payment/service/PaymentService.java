package com.example.payment.service;

import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.PaymentStats;
import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
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
import java.util.List;
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
    public PaymentResponse createPaymentIntent(PaymentRequest request) {
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
                    request.getAmount(),
                    razorpayCurrency,
                    PaymentStatus.PENDING,
                    request.getCustomerEmail(),
                    request.getDescription());

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

    // Handle payment intent status and send appropriate emails
    private PaymentResponse handlePaymentIntentStatus(PaymentIntent paymentIntent, Payment payment) {
        String status = paymentIntent.getStatus();
        logger.info("Payment intent status: {} for payment: {}", status, payment.getStripePaymentId());

        switch (status) {
            case "requires_action", "requires_source_action" -> {
                // Payment requires additional authentication (3D Secure)
                payment.setStatus(PaymentStatus.REQUIRES_ACTION);
                paymentRepository.save(payment);

                return new PaymentResponse(false, "requires_action",
                        paymentIntent.getId(), paymentIntent.getClientSecret());
            }
            case "succeeded" -> {
                // Payment succeeded
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setEmailSent(true);
                paymentRepository.save(payment);

                // Send success email asynchronously
                emailService.sendPaymentSuccessEmail(payment);

                return new PaymentResponse(true, "Payment successful!",
                        paymentIntent.getId(), null);
            }
            case "processing" -> {
                // Payment is processing (for bank transfers, etc.)
                payment.setStatus(PaymentStatus.PROCESSING);
                paymentRepository.save(payment);

                return new PaymentResponse(true, "Payment is being processed",
                        paymentIntent.getId(), null);
            }
            default -> {
                // Payment failed
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(getFailureReason(paymentIntent));
                payment.setEmailSent(true);
                paymentRepository.save(payment);

                // Send failure email asynchronously
                emailService.sendPaymentFailureEmail(payment);

                return new PaymentResponse(false, "Payment failed: " + getFailureReason(paymentIntent));
            }
        }
    }

    // Extract failure reason from payment intent
    private String getFailureReason(PaymentIntent paymentIntent) {
        if (paymentIntent.getLastPaymentError() != null) {
            return paymentIntent.getLastPaymentError().getMessage();
        }
        return "Payment was declined by your bank. Please try a different payment method.";
    }

    // Confirm payment that requires additional action
    public PaymentResponse confirmPayment(String paymentIntentId) {
        logger.info("Confirming payment intent: {}", paymentIntentId);

        try {
            Stripe.apiKey = stripeSecretKey;

            // Retrieve payment intent from Stripe
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            // Find payment in our database
            Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentId(paymentIntentId);
            if (paymentOpt.isEmpty()) {
                return new PaymentResponse(false, "Payment record not found");
            }

            Payment payment = paymentOpt.get();

            // Handle the confirmed payment status
            return handlePaymentIntentStatus(paymentIntent, payment);

        } catch (StripeException e) {
            logger.error("Stripe error confirming payment: {}", e.getMessage(), e);
            return new PaymentResponse(false, "Error confirming payment: " + e.getLocalizedMessage());
        } catch (Exception e) {
            logger.error("Unexpected error confirming payment: {}", e.getMessage(), e);
            return new PaymentResponse(false, "An unexpected error occurred");
        }
    }

    // Process refund
    public PaymentResponse processRefund(String paymentIntentId, Double refundAmount) {
        logger.info("Processing refund for payment: {}, amount: {}", paymentIntentId, refundAmount);

        try {
            Stripe.apiKey = stripeSecretKey;

            // Find payment in database
            Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentId(paymentIntentId);
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

            // Create refund with Stripe
            com.stripe.model.Refund refund = com.stripe.model.Refund.create(
                    com.stripe.param.RefundCreateParams.builder()
                            .setPaymentIntent(paymentIntentId)
                            .setAmount((long) (refundAmount * 100))
                            .setReason(com.stripe.param.RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                            .build());

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

        } catch (StripeException e) {
            logger.error("Stripe error processing refund: {}", e.getMessage(), e);
            return new PaymentResponse(false, "Error processing refund: " + e.getLocalizedMessage());
        } catch (Exception e) {
            logger.error("Unexpected error processing refund: {}", e.getMessage(), e);
            return new PaymentResponse(false, "An unexpected error occurred");
        }
    }

    // Get payments by customer with pagination
    public Page<Payment> getPaymentsByCustomer(String customerEmail, Pageable pageable) {
        return paymentRepository.findByCustomerEmailOrderByCreatedAtDesc(customerEmail, pageable);
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