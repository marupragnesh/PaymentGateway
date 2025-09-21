package com.example.payment.dto;

import jakarta.validation.constraints.*;

public class RefundRequest {

    @NotBlank(message = "Payment Intent ID is required")
    private String razorpayPaymentId;

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Minimum refund amount is $0.01")
    private Double refundAmount;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;

    // Constructors
    public RefundRequest() {}

    public RefundRequest(String razorpayPaymentId, Double refundAmount, String reason) {
        this.razorpayPaymentId = razorpayPaymentId;
        this.refundAmount = refundAmount;
        this.reason = reason;
    }

    // Getters and Setters
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }

    public Double getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Double refundAmount) { this.refundAmount = refundAmount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}