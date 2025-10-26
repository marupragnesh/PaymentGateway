package com.example.payment.service;

import com.example.payment.model.OrderEntity;
import com.example.payment.model.PaymentEntity;
import com.example.payment.repository.OrderRepository;
import com.example.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    private RazorpayClient razorpayClient;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    /**
     * Create a Razorpay order
     */
    @Transactional
    public Map<String, Object> createOrder(Long amount, String currency, String customerName,
                                           String customerEmail, String customerPhone) throws Exception {
        try {
            // Generate unique receipt ID
            String receipt = "rcpt_" + System.currentTimeMillis();

            // Create local order entity first
            OrderEntity orderEntity = new OrderEntity(receipt, amount, currency, "CREATED");
            orderEntity.setCustomerName(customerName);
            orderEntity.setCustomerEmail(customerEmail);
            orderEntity.setCustomerPhone(customerPhone);

            // Prepare Razorpay order request
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount); // amount in paise
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receipt);

            // Add notes (optional metadata)
            JSONObject notes = new JSONObject();
            notes.put("customer_name", customerName);
            notes.put("customer_email", customerEmail);
            orderRequest.put("notes", notes);

            // Create order via Razorpay API
            Order razorpayOrder = razorpayClient.orders.create(orderRequest);

            // Update local order with Razorpay order ID
            orderEntity.setRazorpayOrderId(razorpayOrder.get("id"));
            orderEntity.setNotes(notes.toString());
            orderRepository.save(orderEntity);

//            logger.info("Order created successfully: {}", razorpayOrder.get("id"));

            // Return response to frontend
            Map<String, Object> response = new HashMap<>();
            response.put("orderId", razorpayOrder.get("id"));
            response.put("amount", amount);
            response.put("currency", currency);
            response.put("receipt", receipt);

            return response;

        } catch (RazorpayException e) {
            logger.error("Error creating Razorpay order: {}", e.getMessage());
            throw new Exception("Failed to create order: " + e.getMessage());
        }
    }

    /**
     * Verify payment signature and update order status
     */
    @Transactional
    public Map<String, Object> verifyPayment(String razorpayOrderId, String razorpayPaymentId,
                                             String razorpaySignature) throws Exception {
        try {
            // Step 1: Verify signature
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", razorpayOrderId);
            attributes.put("razorpay_payment_id", razorpayPaymentId);
            attributes.put("razorpay_signature", razorpaySignature);

            boolean isValidSignature = Utils.verifyPaymentSignature(attributes, keySecret);

            if (!isValidSignature) {
                logger.error("Invalid payment signature for order: {}", razorpayOrderId);
                throw new Exception("Payment verification failed: Invalid signature");
            }

            logger.info("Signature verified successfully for payment: {}", razorpayPaymentId);

            // Step 2: Fetch payment details from Razorpay to confirm capture
            Payment payment = razorpayClient.payments.fetch(razorpayPaymentId);
            String paymentStatus = payment.get("status");

            logger.info("Payment status from Razorpay: {}", paymentStatus);

            // Step 3: Update local order
            Optional<OrderEntity> orderOpt = orderRepository.findByRazorpayOrderId(razorpayOrderId);
            if (orderOpt.isEmpty()) {
                throw new Exception("Order not found: " + razorpayOrderId);
            }

            OrderEntity orderEntity = orderOpt.get();

            if ("captured".equals(paymentStatus)) {
                orderEntity.setStatus("PAID");
            } else if ("authorized".equals(paymentStatus)) {
                orderEntity.setStatus("AUTHORIZED");
            } else {
                orderEntity.setStatus("FAILED");
            }

            orderRepository.save(orderEntity);

            // Step 4: Save payment record
            PaymentEntity paymentEntity = new PaymentEntity(razorpayPaymentId, razorpayOrderId,
                    orderEntity.getAmount(), paymentStatus);
            paymentEntity.setRazorpaySignature(razorpaySignature);
            paymentEntity.setPaymentMethod(payment.get("method"));
            paymentEntity.setRawResponse(payment.toString());
            paymentRepository.save(paymentEntity);

            logger.info("Payment verified and saved: {}", razorpayPaymentId);

            // Step 5: Return response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Payment verified successfully");
            response.put("orderId", razorpayOrderId);
            response.put("paymentId", razorpayPaymentId);
            response.put("orderStatus", orderEntity.getStatus());

            return response;

        } catch (RazorpayException e) {
            logger.error("Error verifying payment: {}", e.getMessage());
            throw new Exception("Payment verification failed: " + e.getMessage());
        }
    }

    /**
     * Process webhook events from Razorpay
     */
    @Transactional
    public void processWebhook(String payload, String signature) throws Exception {
        try {
            // Step 1: Verify webhook signature
            boolean isValid = verifyWebhookSignature(payload, signature, webhookSecret);

            if (!isValid) {
                logger.error("Invalid webhook signature");
                throw new Exception("Webhook verification failed");
            }

            logger.info("Webhook signature verified");

            // Step 2: Parse webhook payload
            JSONObject webhookData = new JSONObject(payload);
            String event = webhookData.getString("event");
            JSONObject payloadObj = webhookData.getJSONObject("payload");
            JSONObject paymentEntity = payloadObj.getJSONObject("payment").getJSONObject("entity");

            String razorpayPaymentId = paymentEntity.getString("id");
            String razorpayOrderId = paymentEntity.getString("order_id");
            String status = paymentEntity.getString("status");

            logger.info("Webhook event: {}, Payment ID: {}, Status: {}", event, razorpayPaymentId, status);

            // Step 3: Update order status based on event
            Optional<OrderEntity> orderOpt = orderRepository.findByRazorpayOrderId(razorpayOrderId);
            if (orderOpt.isEmpty()) {
                logger.warn("Order not found for webhook: {}", razorpayOrderId);
                return;
            }

            OrderEntity order = orderOpt.get();

            switch (event) {
                case "payment.captured":
                    order.setStatus("PAID");
                    logger.info("Order marked as PAID: {}", razorpayOrderId);
                    break;
                case "payment.failed":
                    order.setStatus("FAILED");
                    logger.info("Order marked as FAILED: {}", razorpayOrderId);
                    break;
                case "order.paid":
                    order.setStatus("PAID");
                    logger.info("Order marked as PAID (order.paid event): {}", razorpayOrderId);
                    break;
                default:
                    logger.info("Unhandled webhook event: {}", event);
            }

            orderRepository.save(order);

            // Step 4: Save/update payment record if not exists
            Optional<PaymentEntity> existingPayment = paymentRepository.findByRazorpayPaymentId(razorpayPaymentId);
            if (existingPayment.isEmpty()) {
                PaymentEntity newPayment = new PaymentEntity(razorpayPaymentId, razorpayOrderId,
                        paymentEntity.getLong("amount"), status);
                newPayment.setPaymentMethod(paymentEntity.optString("method", "unknown"));
                newPayment.setRawResponse(paymentEntity.toString());
                paymentRepository.save(newPayment);
                logger.info("Payment record created from webhook: {}", razorpayPaymentId);
            }

        } catch (Exception e) {
            logger.error("Error processing webhook: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Get order status
     */
    public Map<String, Object> getOrderStatus(String razorpayOrderId) throws Exception {
        Optional<OrderEntity> orderOpt = orderRepository.findByRazorpayOrderId(razorpayOrderId);
        if (orderOpt.isEmpty()) {
            throw new Exception("Order not found: " + razorpayOrderId);
        }

        OrderEntity order = orderOpt.get();
        List<PaymentEntity> payments = paymentRepository.findByRazorpayOrderId(razorpayOrderId);

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getRazorpayOrderId());
        response.put("receipt", order.getReceipt());
        response.put("amount", order.getAmount());
        response.put("currency", order.getCurrency());
        response.put("status", order.getStatus());
        response.put("createdAt", order.getCreatedAt().toString());
        response.put("payments", payments);

        return response;
    }

    /**
     * Verify webhook signature using HMAC SHA256
     */
    private boolean verifyWebhookSignature(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            String expectedSignature = hexString.toString();
            return expectedSignature.equals(signature);

        } catch (Exception e) {
            logger.error("Error verifying webhook signature: {}", e.getMessage());
            return false;
        }
    }
}