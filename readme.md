# 🔐 Razorpay Payment Integration - Spring Boot

Complete Razorpay payment gateway integration with Spring Boot 3.5.6, Java 17, MySQL, and simple HTML/CSS/JS frontend.

---

## 📋 Prerequisites Checklist

- [x] Java 17 installed
- [x] MySQL running on localhost:3306
- [x] Razorpay Test account created
- [x] Razorpay Test API Keys obtained
- [x] Maven installed (or use IDE's built-in Maven)
- [x] ngrok installed (for webhook testing)

---

## 🚀 Quick Start Guide

### Step 1: Configure Environment Variables

**Linux/Mac (add to `~/.bashrc` or `~/.zshrc`):**
```bash
export RAZORPAY_KEY_ID="rzp_test_YOUR_KEY_ID"
export RAZORPAY_KEY_SECRET="YOUR_KEY_SECRET"
export RAZORPAY_WEBHOOK_SECRET="your_webhook_secret"
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/payment_db"
export SPRING_DATASOURCE_USERNAME="payment_user"
export SPRING_DATASOURCE_PASSWORD="your_password"
```

**Windows (Command Prompt - run as Administrator):**
```cmd
setx RAZORPAY_KEY_ID "rzp_test_YOUR_KEY_ID"
setx RAZORPAY_KEY_SECRET "YOUR_KEY_SECRET"
setx RAZORPAY_WEBHOOK_SECRET "your_webhook_secret"
setx SPRING_DATASOURCE_URL "jdbc:mysql://localhost:3306/payment_db"
setx SPRING_DATASOURCE_USERNAME "payment_user"
setx SPRING_DATASOURCE_PASSWORD "your_password"
```

**IntelliJ IDEA:** Edit Run Configuration → Environment Variables

---

### Step 2: Create MySQL Database
```sql
CREATE DATABASE payment_db;
CREATE USER 'payment_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON payment_db.* TO 'payment_user'@'localhost';
FLUSH PRIVILEGES;
```

---

### Step 3: Build and Run

**Using Maven:**
```bash
mvn clean install
mvn spring-boot:run
```

**Using IDE:** Run `PaymentApplication.java`

**Verify it's running:**
- Backend: http://localhost:8080/api/payment/health
- Frontend: http://localhost:8080/index.html

---

## 🧪 Testing Guide

### Test 1: Health Check
```bash
curl http://localhost:8080/api/payment/health
```

**Expected Response:**
```json
{
  "status": "UP",
  "message": "Payment service is running"
}
```

---

### Test 2: Create Order (via Postman/curl)
```bash
curl -X POST http://localhost:8080/api/payment/create-order \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 50000,
    "currency": "INR",
    "customerName": "Test User",
    "customerEmail": "test@example.com",
    "customerPhone": "9999999999"
  }'
```

**Expected Response:**
```json
{
  "orderId": "order_xxxxxxxxxxxxx",
  "amount": 50000,
  "currency": "INR",
  "receipt": "rcpt_1234567890",
  "keyId": "rzp_test_xxxxxxxxxxxxx"
}
```

---

### Test 3: Frontend Payment Flow

1. Open: http://localhost:8080/index.html
2. Fill in the form (default values are fine)
3. Click **"Pay Now with Razorpay"**
4. Use test card: **4111 1111 1111 1111**
5. CVV: **123**, Expiry: **12/25**
6. Complete payment
7. Check backend logs for verification

**Test Cards (Test Mode Only):**
- **Success:** 4111 1111 1111 1111
- **CVV:** Any 3 digits (e.g., 123)
- **Expiry:** Any future date (e.g., 12/25)
- **Name:** Any name

---

### Test 4: Webhook Testing with ngrok

#### Step 4.1: Start ngrok
```bash
ngrok http 8080
```

**Copy the HTTPS URL** (e.g., `https://abc123.ngrok-free.app`)

#### Step 4.2: Configure Razorpay Webhook

1. Go to: https://dashboard.razorpay.com/app/webhooks
2. Click **"Create New Webhook"**
3. **Webhook URL:** `https://YOUR_NGROK_URL/api/payment/webhook`
4. **Active Events:** Select:
    - `payment.captured`
    - `payment.failed`
    - `order.paid`
5. Click **"Create Webhook"**
6. **Copy the Webhook Secret** and update your environment variable:
```bash
   export RAZORPAY_WEBHOOK_SECRET="your_webhook_secret_from_dashboard"
```
7. **Restart your Spring Boot app**

#### Step 4.3: Test Webhook

1. Make a test payment via frontend
2. Check your Spring Boot logs for:
```
   Received webhook with signature: ...
   Webhook signature verified
   Webhook event: payment.captured
   Order marked as PAID: order_xxxxx
```

---

## 📊 Database Schema

### `orders` table
```sql
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt VARCHAR(255) NOT NULL UNIQUE,
    amount BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    razorpay_order_id VARCHAR(255) UNIQUE,
    status VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    notes TEXT,
    customer_email VARCHAR(255),
    customer_phone VARCHAR(20),
    customer_name VARCHAR(255)
);
```

### `payments` table
```sql
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    razorpay_payment_id VARCHAR(255) NOT NULL UNIQUE,
    razorpay_order_id VARCHAR(255) NOT NULL,
    razorpay_signature VARCHAR(512),
    amount BIGINT NOT NULL,
    payment_method VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    raw_response TEXT
);
```

---

## 🔌 API Endpoints

### 1. Health Check
```
GET /api/payment/health
```

### 2. Create Order
```
POST /api/payment/create-order
Content-Type: application/json

{
  "amount": 50000,
  "currency": "INR",
  "customerName": "John Doe",
  "customerEmail": "john@example.com",
  "customerPhone": "9999999999"
}
```

### 3. Verify Payment
```
POST /api/payment/verify
Content-Type: application/json

{
  "razorpay_order_id": "order_xxxxx",
  "razorpay_payment_id": "pay_xxxxx",
  "razorpay_signature": "xxxxx"
}
```

### 4. Webhook
```
POST /api/payment/webhook
X-Razorpay-Signature: <signature>
Content-Type: application/json

<Razorpay webhook payload>
```

### 5. Get Order Status
```
GET /api/payment/status/{orderId}
```

---

## 🐛 Troubleshooting

### Issue: "Cannot connect to backend API"
- Verify Spring Boot is running on port 8080
- Check `http://localhost:8080/api/payment/health`
- Check console for startup errors

### Issue: "Order creation failed"
- Verify Razorpay API keys are correct
- Check environment variables are set
- Look at Spring Boot logs for detailed error

### Issue: "Payment verification failed"
- Check if `RAZORPAY_KEY_SECRET` is correct
- Verify signature is being sent from frontend
- Check backend logs for signature verification details

### Issue: "Webhook signature invalid"
- Verify `RAZORPAY_WEBHOOK_SECRET` matches dashboard
- Restart Spring Boot after updating webhook secret
- Check ngrok URL is correct in Razorpay dashboard

### Issue: Database connection failed
- Verify MySQL is running
- Check database credentials in environment variables
- Verify `payment_db` database exists

---

## 📦 Project Structure
```
payment/
├── src/
│   ├── main/
│   │   ├── java/com/example/payment/
│   │   │   ├── PaymentApplication.java
│   │   │   ├── config/
│   │   │   │   └── RazorpayClientConfig.java
│   │   │   ├── controller/
│   │   │   │   └── PaymentController.java
│   │   │   ├── service/
│   │   │   │   └── PaymentService.java
│   │   │   ├── model/
│   │   │   │   ├── OrderEntity.java
│   │   │   │   └── PaymentEntity.java
│   │   │   └── repository/
│   │   │       ├── OrderRepository.java
│   │   │       └── PaymentRepository.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/
│   │           └── index.html
├── pom.xml
└── README.md
```

---

## 🔒 Security Checklist for Production

- [ ] Change database password
- [ ] Use Live Razorpay API keys (not Test keys)
- [ ] Remove `@CrossOrigin(origins = "*")` from controller
- [ ] Add proper CORS configuration
- [ ] Enable HTTPS (SSL/TLS)
- [ ] Add rate limiting on API endpoints
- [ ] Add authentication/authorization
- [ ] Store sensitive data encrypted
- [ ] Set up proper logging and monitoring
- [ ] Add input validation and sanitization
- [ ] Configure proper webhook secret rotation policy

---

## 📚 Resources

- **Razorpay API Docs:** https://razorpay.com/docs/api/
- **Razorpay Java SDK:** https://github.com/razorpay/razorpay-java
- **Test Cards:** https://razorpay.com/docs/payments/payments/test-card-details/
- **Webhooks:** https://razorpay.com/docs/webhooks/

---

## ✅ Next Steps After Testing

1. **Test all flows thoroughly in Test Mode**
2. **Verify webhook events are processed correctly**
3. **Test edge cases** (payment failures, network errors, etc.)
4. **Review logs** for any errors or warnings
5. **When ready for production:**
    - Switch to Live API keys
    - Update webhook URL to production domain
    - Implement security checklist items
    - Set up monitoring and alerts

---

**Made with ❤️ using Spring Boot 3.5.6 + Java 17 + Razorpay SDK 1.4.8**