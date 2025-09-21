package com.example.payment.entity;

// Payment Status Enum
public enum PaymentStatus {
    PENDING,
    REQUIRES_ACTION,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED
}