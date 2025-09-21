package com.example.payment.dto;

public class PaymentResponse {
    private boolean success;
    private String message;
    private String paymentId;
    private String clientSecret;

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

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
}