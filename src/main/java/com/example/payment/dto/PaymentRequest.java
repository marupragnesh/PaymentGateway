package com.example.payment.dto;

import jakarta.validation.constraints.*;

public class PaymentRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.50", message = "Minimum amount is $0.50")
    @DecimalMax(value = "999999.99", message = "Maximum amount is $999,999.99")
    private Double amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3 uppercase letters (e.g., USD)")
    private String currency;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    private String customerEmail;

    @NotBlank(message = "Description is required")
    @Size(min = 3, max = 255, message = "Description must be between 3 and 255 characters")
    private String description;

    @NotBlank(message = "Payment method ID is required")
    private String paymentMethodId;

    // Constructors
    public PaymentRequest() {}

    public PaymentRequest(Double amount, String currency, String customerEmail,
                          String description, String paymentMethodId) {
        this.amount = amount;
        this.currency = currency;
        this.customerEmail = customerEmail;
        this.description = description;
        this.paymentMethodId = paymentMethodId;
    }

    // Getters and Setters
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }
}