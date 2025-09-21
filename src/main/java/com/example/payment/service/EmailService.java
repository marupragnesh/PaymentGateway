package com.example.payment.service;

import com.example.payment.entity.Payment;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.support.email}")
    private String supportEmail;

    @Value("${app.name}")
    private String appName;

    // Send payment success email (async for better performance)
    @Async
    public void sendPaymentSuccessEmail(Payment payment) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, appName);
            helper.setTo(payment.getCustomerEmail());
            helper.setSubject("Payment Successful - " + appName);

            String emailContent = buildSuccessEmailContent(payment);
            helper.setText(emailContent, true); // true means HTML content

            mailSender.send(message);
            logger.info("Success email sent to: {}", payment.getCustomerEmail());

        } catch (MessagingException e) {
            logger.error("Failed to send success email to: {}", payment.getCustomerEmail(), e);
        } catch (Exception e) {
            logger.error("Unexpected error sending success email: {}", e.getMessage(), e);
        }
    }

    // Send payment failure email
    @Async
    public void sendPaymentFailureEmail(Payment payment) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, appName);
            helper.setTo(payment.getCustomerEmail());
            helper.setSubject("Payment Failed - " + appName);

            String emailContent = buildFailureEmailContent(payment);
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Failure email sent to: {}", payment.getCustomerEmail());

        } catch (MessagingException e) {
            logger.error("Failed to send failure email to: {}", payment.getCustomerEmail(), e);
        } catch (Exception e) {
            logger.error("Unexpected error sending failure email: {}", e.getMessage(), e);
        }
    }

    // Build success email HTML content
    private String buildSuccessEmailContent(Payment payment) {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a");

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; }
                    .header { background: #28a745; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; }
                    .payment-details { background: #f8f9fa; padding: 15px; border-radius: 5px; margin: 20px 0; }
                    .footer { background: #f8f9fa; padding: 15px; text-align: center; font-size: 12px; color: #666; }
                    .success-icon { font-size: 48px; color: #28a745; }
                </style>
            </head>
            <body>
                <div class="header">
                    <div class="success-icon">✓</div>
                    <h1>Payment Successful!</h1>
                </div>
                
                <div class="content">
                    <p>Dear Customer,</p>
                    
                    <p>Thank you for your payment! Your transaction has been completed successfully.</p>
                    
                    <div class="payment-details">
                        <h3>Payment Details:</h3>
                        <p><strong>Amount:</strong> %s</p>
                        <p><strong>Description:</strong> %s</p>
                        <p><strong>Payment ID:</strong> %s</p>
                        <p><strong>Date:</strong> %s</p>
                        <p><strong>Payment Method:</strong> %s ending in %s</p>
                    </div>
                    
                    <p>A receipt for this payment has been automatically generated. Please keep this email for your records.</p>
                    
                    <p>If you have any questions about this payment, please contact us at <a href="mailto:%s">%s</a>.</p>
                    
                    <p>Thank you for your business!</p>
                    
                    <p>Best regards,<br>The %s Team</p>
                </div>
                
                <div class="footer">
                    <p>This is an automated message. Please do not reply to this email.</p>
                </div>
            </body>
            </html>
            """,
                currencyFormat.format(payment.getAmount()),
                payment.getDescription(),
                payment.getRazorpayPaymentId(),
                payment.getCreatedAt().format(dateFormat),
                payment.getCardBrand() != null ? payment.getCardBrand().toUpperCase() : "Card",
                payment.getCardLast4() != null ? payment.getCardLast4() : "****",
                supportEmail,
                supportEmail,
                appName
        );
    }

    // Build failure email HTML content
    private String buildFailureEmailContent(Payment payment) {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a");

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; }
                    .header { background: #dc3545; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; }
                    .payment-details { background: #f8f9fa; padding: 15px; border-radius: 5px; margin: 20px 0; }
                    .footer { background: #f8f9fa; padding: 15px; text-align: center; font-size: 12px; color: #666; }
                    .error-icon { font-size: 48px; color: #dc3545; }
                    .retry-button { background: #007bff; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block; margin: 10px 0; }
                </style>
            </head>
            <body>
                <div class="header">
                    <div class="error-icon">✗</div>
                    <h1>Payment Failed</h1>
                </div>
                
                <div class="content">
                    <p>Dear Customer,</p>
                    
                    <p>We were unable to process your payment. No charges have been made to your account.</p>
                    
                    <div class="payment-details">
                        <h3>Payment Details:</h3>
                        <p><strong>Amount:</strong> %s</p>
                        <p><strong>Description:</strong> %s</p>
                        <p><strong>Date:</strong> %s</p>
                        <p><strong>Reason:</strong> %s</p>
                    </div>
                    
                    <p>You can try your payment again by clicking the button below:</p>
                    
                    <a href="https://your-domain.com" class="retry-button">Try Payment Again</a>
                    
                    <p>If you continue to have issues, please try:</p>
                    <ul>
                        <li>Using a different payment method</li>
                        <li>Contacting your bank to ensure the transaction is approved</li>
                        <li>Double-checking your payment information</li>
                    </ul>
                    
                    <p>If you need assistance, please contact us at <a href="mailto:%s">%s</a>.</p>
                    
                    <p>Best regards,<br>The %s Team</p>
                </div>
                
                <div class="footer">
                    <p>This is an automated message. Please do not reply to this email.</p>
                </div>
            </body>
            </html>
            """,
                currencyFormat.format(payment.getAmount()),
                payment.getDescription(),
                payment.getCreatedAt().format(dateFormat),
                payment.getFailureReason() != null ? payment.getFailureReason() : "Payment declined by bank",
                supportEmail,
                supportEmail,
                appName
        );
    }
}