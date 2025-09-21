package com.example.payment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_stripe_payment_id", columnList = "stripePaymentId"),
        @Index(name = "idx_customer_email", columnList = "customerEmail"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_created_at", columnList = "createdAt")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String stripePaymentId;

    @Column(nullable = false, precision = 10, scale = 2)
    @DecimalMin(value = "0.50", message = "Minimum amount is $0.50")
    @DecimalMax(value = "999999.99", message = "Maximum amount is $999,999.99")
    private Double amount;

    @Column(nullable = false, length = 3)
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3 uppercase letters")
    private String currency;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(nullable = false, length = 100)
    @Email(message = "Invalid email format")
    private String customerEmail;

    @Column(nullable = false, length = 255)
    @NotBlank(message = "Description is required")
    private String description;

    @Column(length = 500)
    private String failureReason;

    @Column(length = 100)
    private String paymentMethodType; // card, bank_transfer, etc.

    @Column(length = 4)
    private String cardLast4; // Last 4 digits of card

    @Column(length = 50)
    private String cardBrand; // visa, mastercard, etc.

    @Column(nullable = false)
    private Boolean refunded = false;

    @Column(precision = 10, scale = 2)
    private Double refundedAmount = 0.0;

    @Column(nullable = false)
    private Boolean emailSent = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Default constructor
    public Payment() {}

    // Constructor for creating new payment
    public Payment(String stripePaymentId, Double amount, String currency,
                   PaymentStatus status, String customerEmail, String description) {
        this.stripePaymentId = stripePaymentId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.customerEmail = customerEmail;
        this.description = description;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStripePaymentId() { return stripePaymentId; }
    public void setStripePaymentId(String stripePaymentId) { this.stripePaymentId = stripePaymentId; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getPaymentMethodType() { return paymentMethodType; }
    public void setPaymentMethodType(String paymentMethodType) { this.paymentMethodType = paymentMethodType; }

    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }

    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }

    public Boolean getRefunded() { return refunded; }
    public void setRefunded(Boolean refunded) { this.refunded = refunded; }

    public Double getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(Double refundedAmount) { this.refundedAmount = refundedAmount; }

    public Boolean getEmailSent() { return emailSent; }
    public void setEmailSent(Boolean emailSent) { this.emailSent = emailSent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

