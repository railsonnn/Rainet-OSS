# Rainet OSS/BSS Platform - Setup & Deployment Guide

## 🎯 Pre-Implementation Checklist

Before running the Rainet platform, ensure you have:

- [ ] Java 17+ installed
- [ ] PostgreSQL 13+ database
- [ ] MikroTik router with API enabled (port 8728/8729)
- [ ] FreeRADIUS server configured
- [ ] Asaas or Gerencianet API credentials
- [ ] SMTP server for email notifications
- [ ] Docker/Kubernetes (optional, for containerization)

---

## 📋 Step 1: Database Setup

### Create PostgreSQL Database

```sql
-- Connect to PostgreSQL as superuser
psql -U postgres

-- Create database
CREATE DATABASE rainet_oss;

-- Create user with permissions
CREATE USER rainet_user WITH PASSWORD 'secure_password_here';
ALTER ROLE rainet_user SET client_encoding TO 'utf8';
ALTER ROLE rainet_user SET default_transaction_isolation TO 'read committed';
ALTER ROLE rainet_user SET default_transaction_deferrable TO on;
ALTER ROLE rainet_user SET timezone TO 'UTC';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE rainet_oss TO rainet_user;
GRANT CONNECT ON DATABASE rainet_oss TO rainet_user;

-- Connect to the new database
\c rainet_oss rainet_user

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Apply Flyway migrations (automatic on app startup)
```

---

## 📦 Step 2: Build & Dependencies

### Build the Project

```bash
# Navigate to project directory
cd /path/to/Rainet-OSS

# Build with Maven
mvn clean install

# Build Docker image (optional)
mvn spring-boot:build-image

# Run locally
mvn spring-boot:run
```

### Verify Dependencies

```bash
# Check if all dependencies resolved
mvn dependency:tree | grep -E "FAILURE|ERROR"

# Verify MikroTik library loaded
mvn dependency:tree | grep mikrotik
```

---

## 🔧 Step 3: Configuration

### Create application.yml

```bash
# Copy example configuration
cp application.yml.example application.yml

# Edit with your values
nano application.yml
```

### Essential Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rainet_oss
    username: rainet_user
    password: your_secure_password

mikrotik:
  api:
    host: 192.168.1.1          # Router IP
    port: 8728                 # API port
    username: admin
    password: router_password
    timeout-seconds: 30

radius:
  server: 127.0.0.1            # FreeRADIUS server
  port: 1812
  secret: your_radius_secret

pix:
  gateway: asaas
  asaas:
    api-key: your_asaas_key
    api-url: https://api.asaas.com/v3

jwt:
  secret: your_very_long_random_jwt_secret_key
  expiration-ms: 86400000       # 24 hours
```

---

## 🔐 Step 4: Security Setup

### Generate JWT Secret

```bash
# Generate a strong random JWT secret
openssl rand -base64 32
# Output: abc123def456ghi789...

# Add to application.yml
jwt.secret: abc123def456ghi789...
```

### Configure HTTPS (Production)

```bash
# Generate self-signed certificate (development)
keytool -genkey -alias rainet -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -keystore rainet-keystore.p12 \
  -validity 365

# Add to application.yml (production)
server:
  ssl:
    key-store: file:rainet-keystore.p12
    key-store-password: ${SSL_PASSWORD}
    key-store-type: PKCS12
```

### Create Admin User (Initial Setup)

```sql
-- Insert first admin user into database (after app starts)
-- SQL migration will be created automatically by Flyway

-- Or via REST API (after app is running):
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin@rainet.local",
    "email": "admin@rainet.local",
    "password": "SecurePassword123!",
    "role": "ADMIN"
  }'
```

---

## 🌐 Step 5: MikroTik Router Setup

### Enable API on Router

```routeros
# Connect to MikroTik via SSH/Winbox
/ip service
set api address=0.0.0.0
set api disabled=no
set api port=8728

# Enable API with TLS (recommended)
set api-ssl address=0.0.0.0
set api-ssl disabled=no
set api-ssl port=8729

# Create API user with strong password
/user
add name=rainet_api group=admin password=StrongPassword123!

# Verify connectivity
/system package print | grep api
```

### Test API Connection

```bash
# From Rainet application
curl -X POST http://localhost:8080/api/v1/provisioning/test-conn \
  -H "X-Tenant-ID: your-tenant-uuid" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## 📡 Step 6: FreeRADIUS Configuration

### Install FreeRADIUS

```bash
# Ubuntu/Debian
sudo apt-get install freeradius freeradius-utils

# CentOS/RHEL
sudo yum install freeradius freeradius-utils
```

### Configure RADIUS Server

```bash
# Edit clients.conf
sudo nano /etc/freeradius/clients.conf

# Add MikroTik router as client:
client 192.168.1.1 {
    secret = your_radius_secret
    shortname = rainet_router
}
```

### Enable RADIUS Modules

```bash
# Edit radiusd.conf
sudo nano /etc/freeradius/radiusd.conf

# Ensure these modules are enabled:
# - sql (for database authentication)
# - mschap (for Windows/PPP)
# - pap (for PAP authentication)

# Restart RADIUS
sudo systemctl restart freeradius
```

### Test RADIUS Authentication

```bash
# Test with radtest utility
radtest customer@isp.com password123 127.0.0.1 0 your_radius_secret

# Expected output:
# Received Access-Accept packet
```

---

## 💳 Step 7: PIX Payment Gateway Setup

### Setup Asaas Account

```bash
# 1. Create account at https://asaas.com
# 2. Generate API key from dashboard
# 3. Add to environment variables:

export ASAAS_API_KEY="your_api_key_here"

# 4. Configure webhook URL in Asaas dashboard:
# https://your-domain.com/api/v1/billing/pix/webhook
```

### Setup Gerencianet Account (Alternative)

```bash
# 1. Create account at https://gerencianet.com.br
# 2. Generate client credentials
# 3. Add to environment:

export GERENCIANET_CLIENT_ID="your_client_id"
export GERENCIANET_CLIENT_SECRET="your_client_secret"
```

### Test PIX Webhook

```bash
# Generate invoice and QR code
curl -X POST http://localhost:8080/api/v1/billing/pix/generate \
  -H "X-Tenant-ID: your-tenant-uuid" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust_123",
    "amount": 29.90,
    "description": "Invoice #001"
  }'

# Simulate webhook callback (for testing)
curl -X POST http://localhost:8080/api/v1/billing/pix/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt_123",
    "eventType": "payment_confirmed",
    "paymentId": "pix_123",
    "invoiceId": "inv_123",
    "status": "CONFIRMED",
    "amount": 29.90,
    "paidAt": "2024-01-15T10:30:00Z"
  }'
```

---

## 🚀 Step 8: Deploy & Run

### Option A: Local Development

```bash
# Build and run
mvn clean install
mvn spring-boot:run

# Application will start at http://localhost:8080
# Health check: http://localhost:8080/health
```

### Option B: Docker

```bash
# Build Docker image
mvn spring-boot:build-image

# Run container
docker run -d \
  --name rainet-oss \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/rainet_oss \
  -e SPRING_DATASOURCE_USERNAME=rainet_user \
  -e SPRING_DATASOURCE_PASSWORD=password \
  -e MIKROTIK_API_HOST=192.168.1.1 \
  -e ASAAS_API_KEY=your_key \
  rainet-oss:0.1.0-SNAPSHOT
```

### Option C: Docker Compose

```bash
# Create docker-compose.yml (see project root)
docker-compose up -d

# View logs
docker-compose logs -f rainet-oss
```

### Option D: Kubernetes

```bash
# Create namespace
kubectl create namespace rainet

# Deploy application
kubectl apply -f k8s/deployment.yaml -n rainet

# Check pods
kubectl get pods -n rainet
kubectl logs -f deployment/rainet-oss -n rainet
```

---

## ✅ Verification Checklist

After deployment, verify each component:

### Health Checks

```bash
# API health
curl http://localhost:8080/health

# Database connectivity
curl http://localhost:8080/health/db

# Metrics
curl http://localhost:8080/metrics
```

### Test Provisioning

```bash
# 1. Get router ID
curl -H "X-Tenant-ID: your-tenant-id" \
     -H "Authorization: Bearer TOKEN" \
     http://localhost:8080/api/v1/routers

# 2. Test connection
curl -X POST http://localhost:8080/api/v1/provisioning/test-conn \
     -H "X-Tenant-ID: your-tenant-id" \
     -H "Authorization: Bearer TOKEN"

# Expected: {"success": true}
```

### Test RADIUS Authentication

```bash
# Create test customer
curl -X POST http://localhost:8080/api/v1/customers \
     -H "X-Tenant-ID: your-tenant-id" \
     -H "Authorization: Bearer TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "email": "test@customer.com",
       "password": "password123",
       "planId": "plan_starter"
     }'

# Test RADIUS auth
radtest test@customer.com password123 127.0.0.1 0 your_radius_secret
```

### Test PIX Payment

```bash
# Create invoice
curl -X POST http://localhost:8080/api/v1/invoices \
     -H "X-Tenant-ID: your-tenant-id" \
     -H "Authorization: Bearer TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "customerId": "cust_123",
       "amount": 29.90,
       "dueDate": "2024-02-15"
     }'

# Generate QR code
curl -X POST http://localhost:8080/api/v1/billing/pix/generate \
     -H "X-Tenant-ID: your-tenant-id" \
     -H "Authorization: Bearer TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "invoiceId": "inv_123"
     }'
```

---

## 🧪 Run Test Suite

### Execute E2E Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=IsoPilotE2ETest

# Run with coverage
mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Manual Testing Checklist

- [ ] Router auto-configuration works
- [ ] Customer PPPoE connection succeeds
- [ ] Bandwidth limits are enforced
- [ ] PIX payment triggers unlock
- [ ] Rollback restores previous config
- [ ] Audit logs capture all operations
- [ ] Multi-tenant isolation works
- [ ] RBAC prevents unauthorized access

---

## 📊 Monitoring & Logs

### Application Logs

```bash
# View application logs
tail -f logs/rainet-oss.log

# Filter by level
grep ERROR logs/rainet-oss.log
grep WARN logs/rainet-oss.log

# Search for specific operation
grep "PROVISIONING_APPLY" logs/rainet-oss.log
```

### Database Logs

```bash
# PostgreSQL slow query log
sudo grep "duration:" /var/log/postgresql/postgresql.log | head -20

# Enable slow query logging
sudo nano /etc/postgresql/postgresql.conf
# Set: log_min_duration_statement = 1000  # Log queries > 1 second
```

### RADIUS Logs

```bash
# FreeRADIUS debug
sudo radiusd -X

# View logs
sudo tail -f /var/log/freeradius/radius.log
```

---

## 🔍 Troubleshooting

### MikroTik Connection Issues

```bash
# Test API connectivity
nmap -p 8728 192.168.1.1

# Check firewall rules
ssh admin@192.168.1.1
/ip firewall filter print

# Verify API credentials
curl -u admin:password http://192.168.1.1:8728/api/resource/system/identity/print
```

### RADIUS Authentication Failures

```bash
# Test RADIUS server
radtest testuser testpass 127.0.0.1 0 testing123

# Enable RADIUS debug
sudo radiusd -X

# Check SQL database for users
psql -d rainet_oss -c "SELECT * FROM users WHERE email='test@customer.com';"
```

### PIX Payment Integration Issues

```bash
# Test Asaas API
curl -H "Authorization: Bearer YOUR_API_KEY" \
     https://api.asaas.com/v3/account

# Check webhook logs
grep "pix" logs/rainet-oss.log | grep webhook
```

---

## 🎬 Next Steps

1. **Run Pilot ISP Checklist** (IsoPilotE2ETest.md)
2. **Create real MikroTik test lab** with 1-2 routers
3. **Onboard first customers** (5-10 test users)
4. **Validate PPPoE connections** with real clients
5. **Test PIX payment flow** end-to-end
6. **Monitor production metrics** for 24-48 hours
7. **Performance optimization** based on metrics
8. **Scale to production** (multiple routers, thousands of customers)

---

## 📞 Support & Help

For issues, check:

1. **Logs**: `logs/rainet-oss.log`
2. **Database**: PostgreSQL error logs
3. **Router**: MikroTik WebFig or SSH
4. **RADIUS**: FreeRADIUS debug mode
5. **PIX**: Asaas/Gerencianet API docs

---

**Happy deploying! 🚀**

