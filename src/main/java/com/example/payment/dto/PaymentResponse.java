package com.example.payment.dto;

public class PaymentResponse {
    private boolean success;
    private String message;
    private String paymentId;
    private String clientSecret;
    private String orderId;
    private String status;
    private Double amount;
    private String currency;
    private String keyId;

    // Constructors
    public PaymentResponse() {}

    public PaymentResponse(boolean success, String message, String paymentId, String clientSecret) {
        this.success = success;
        this.message = message;
        this.paymentId = paymentId;
        this.clientSecret = clientSecret;
    }

    public PaymentResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public PaymentResponse(boolean success, String message, String paymentId, String clientSecret, String orderId, String status, Double amount, String currency, String keyId) {
        this.success = success;
        this.message = message;
        this.paymentId = paymentId;
        this.clientSecret = clientSecret;
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.keyId = keyId;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
}