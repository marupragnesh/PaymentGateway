package com.example.payment.service;

import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.repository.PaymentRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EmailService emailService;

    public void handleRazorpayEvent(String payload, String signature) {
        logger.info("Processing Razorpay webhook event");
        
        try {
            JSONObject webhookData = new JSONObject(payload);
            String eventType = webhookData.getString("event");
            
            switch (eventType) {
                case "payment.authorized" -> handlePaymentAuthorized(webhookData);
                case "payment.failed" -> handlePaymentFailed(webhookData);
                case "payment.captured" -> handlePaymentCaptured(webhookData);
                case "refund.processed" -> handleRefundProcessed(webhookData);
                default -> logger.info("Unhandled event type: {}", eventType);
            }
        } catch (Exception e) {
            logger.error("Error processing webhook: {}", e.getMessage(), e);
        }
    }

    private void handlePaymentAuthorized(JSONObject webhookData) {
        JSONObject paymentEntity = webhookData.getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");
        
        String orderId = paymentEntity.getString("order_id");
        String paymentId = paymentEntity.getString("id");

        Optional<Payment> paymentOpt = paymentRepository.findByRazorpayOrderId(orderId);
        if (paymentOpt.isEmpty()) {
            logger.error("Payment not found for Order ID: {}", orderId);
            return;
        }

        Payment payment = paymentOpt.get();
        payment.setStatus(PaymentStatus.SUCCESS);
        
        // Update payment ID with the actual payment ID (not just order ID)
        payment.setRazorpayPaymentId(paymentId);
        
        // Extract and save card details if available
        if (paymentEntity.has("card")) {
            JSONObject card = paymentEntity.getJSONObject("card");
            payment.setCardBrand(card.getString("network"));
            payment.setCardLast4(card.getString("last4"));
        }

        if (!payment.getEmailSent()) {
            payment.setEmailSent(true);
            emailService.sendPaymentSuccessEmail(payment);
        }

        paymentRepository.save(payment);
        logger.info("Payment authorized: {}", payment.getRazorpayPaymentId());
    }

    private void handlePaymentFailed(JSONObject webhookData) {
        JSONObject paymentEntity = webhookData.getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");
        
        String orderId = paymentEntity.getString("order_id");

        Optional<Payment> paymentOpt = paymentRepository.findByRazorpayOrderId(orderId);
        if (paymentOpt.isEmpty()) return;

        Payment payment = paymentOpt.get();
        payment.setStatus(PaymentStatus.FAILED);

        if (paymentEntity.has("error_description")) {
            payment.setFailureReason(paymentEntity.getString("error_description"));
        }

        if (!payment.getEmailSent()) {
            payment.setEmailSent(true);
            emailService.sendPaymentFailureEmail(payment);
        }

        paymentRepository.save(payment);
    }

    private void handlePaymentCaptured(JSONObject webhookData) {
        JSONObject paymentEntity = webhookData.getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");
        
        String orderId = paymentEntity.getString("order_id");

        Optional<Payment> paymentOpt = paymentRepository.findByRazorpayOrderId(orderId);
        if (paymentOpt.isEmpty()) return;

        Payment payment = paymentOpt.get();
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
        logger.info("Payment captured: {}", payment.getRazorpayPaymentId());
    }

    private void handleRefundProcessed(JSONObject webhookData) {
        JSONObject refundEntity = webhookData.getJSONObject("payload")
                .getJSONObject("refund")
                .getJSONObject("entity");
        
        String paymentId = refundEntity.getString("payment_id");

        Optional<Payment> paymentOpt = paymentRepository.findByRazorpayPaymentId(paymentId);
        if (paymentOpt.isEmpty()) return;

        Payment payment = paymentOpt.get();
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        logger.info("Payment refunded: {}", payment.getRazorpayPaymentId());
    }


}