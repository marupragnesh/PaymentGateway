package com.example.payment.dto;

public class PaymentStats {
    private final Long successfulPayments;
    private final Double totalRevenue;
    private final Long failedPayments;
    private final Long totalPayments;

    public PaymentStats(Long successfulPayments, Double totalRevenue, Long failedPayments, Long totalPayments) {
        this.successfulPayments = successfulPayments;
        this.totalRevenue = totalRevenue;
        this.failedPayments = failedPayments;
        this.totalPayments = totalPayments;
    }

    // Getters
    public Long getSuccessfulPayments() { return successfulPayments; }
    public Double getTotalRevenue() { return totalRevenue; }
    public Long getFailedPayments() { return failedPayments; }
    public Long getTotalPayments() { return totalPayments; }

    // Calculated fields
    public Double getSuccessRate() {
        if (totalPayments == 0) return 0.0;
        return (successfulPayments.doubleValue() / totalPayments.doubleValue()) * 100;
    }

    public Double getAverageTransactionAmount() {
        if (successfulPayments == 0) return 0.0;
        return totalRevenue / successfulPayments.doubleValue();
    }
}