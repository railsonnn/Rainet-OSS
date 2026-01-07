# Automatic Customer Blocking for Delinquency

## Overview

This feature implements automatic blocking of customers who have overdue invoices (inadimplentes). When a customer fails to pay their invoice by the due date, the system automatically blocks their access, applying a BLOCKED RADIUS profile with minimal bandwidth. Upon payment, the customer is automatically unblocked.

## Key Features

### 1. Automatic Blocking
- **Scheduled Task**: Runs daily at 2 AM (configurable via cron expression)
- **Invoice Status**: Changes PENDING invoices to OVERDUE when past due date
- **Customer Status**: Sets `blocked = true` for customers with overdue invoices
- **RADIUS Profile**: Blocked customers receive "BLOCKED" profile with 1 Kbps bandwidth (125 bytes/sec)

### 2. BLOCKED Profile in RADIUS
- **Profile Name**: "BLOCKED"
- **Bandwidth**: 125 bytes/sec upload/download (1 Kbps)
- **Authentication**: Still authenticated but with severely restricted access
- **Message**: "Your account is blocked due to overdue payment. Please contact support."
- **MikroTik Rate Limit**: `125/125` in Mikrotik-Rate-Limit attribute

### 3. Automatic Unblocking
- **Payment Detection**: When any invoice is paid
- **Smart Unblocking**: Only unblocks if NO other overdue invoices exist
- **Immediate Effect**: Next PPPoE authentication will return full bandwidth
- **PIX Integration**: Works with PIX payment webhook for instant unblocking

## Implementation Details

### Database Schema

#### customers table
```sql
ALTER TABLE customers ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT false;
```

### Core Services

#### DelinquencyService
Location: `src/main/java/com/isp/platform/billing/service/DelinquencyService.java`

**Responsibilities:**
- Check for overdue invoices daily
- Mark invoices as OVERDUE status
- Block customers with overdue invoices
- Provide method to check if customer has overdue invoices

**Schedule:**
```java
@Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
public void checkAndBlockDelinquentCustomers()
```

**Key Methods:**
- `checkAndBlockDelinquentCustomers()`: Scheduled task
- `manualDelinquencyCheck()`: Manually trigger check (for testing/admin)
- `hasOverdueInvoices(customerId)`: Check if customer has overdue invoices

#### BillingService Enhancement
Location: `src/main/java/com/isp/platform/billing/service/BillingService.java`

**New Functionality:**
- Automatically unblock customer after payment
- Check for remaining overdue invoices before unblocking
- Integration with DelinquencyService

**Payment Flow:**
1. Mark invoice as PAID
2. Check if customer has other overdue invoices
3. If no overdue invoices, unblock customer
4. If overdue invoices exist, keep customer blocked

#### RadiusServerService Update
Location: `src/main/java/com/isp/platform/provisioning/radius/RadiusServerService.java`

**Authentication Flow:**
1. Find customer by username (UUID or document)
2. Check if customer is blocked
3. If blocked, return BLOCKED profile with minimal bandwidth
4. If not blocked and active, return full plan bandwidth

**BLOCKED Profile Response:**
```java
{
  "authenticated": true,
  "profileName": "BLOCKED",
  "uploadMbps": 0,
  "downloadMbps": 0,
  "attributes": {
    "Mikrotik-Rate-Limit": "125/125",
    "Mikrotik-Queue-Name": "BLOCKED",
    "Reply-Message": "Your account is blocked due to overdue payment..."
  }
}
```

### Repository Methods

#### InvoiceRepository
```java
// Find all overdue invoices (PENDING status past due date)
List<Invoice> findOverdueInvoices(InvoiceStatus status, LocalDate currentDate);

// Find overdue invoices for specific customer
List<Invoice> findOverdueInvoicesByCustomer(String customerId, LocalDate currentDate);

// Find invoices by status for a customer
List<Invoice> findByCustomerIdAndStatus(String customerId, InvoiceStatus status);
```

#### CustomerRepository
```java
// Find customer by document and tenant
Optional<Customer> findByDocumentAndTenantId(String document, UUID tenantId);

// Find all customers for a tenant
List<Customer> findByTenantId(UUID tenantId);
```

## Usage Examples

### Scenario 1: Customer with Overdue Invoice

**Day 0 (Invoice Due Date):**
- Invoice status: PENDING
- Customer status: ACTIVE, blocked = false
- RADIUS: Full bandwidth (e.g., 10/20 Mbps)

**Day 1 (After 2 AM scheduled task):**
- Invoice status: OVERDUE
- Customer status: ACTIVE, blocked = true
- RADIUS: BLOCKED profile (125 bytes/sec)

**Customer tries to connect:**
- PPPoE authentication succeeds
- Receives BLOCKED profile
- Can connect but with 1 Kbps bandwidth
- Sees message about overdue payment

### Scenario 2: Customer Pays Invoice

**Before Payment:**
- Invoice #1: OVERDUE
- Customer: blocked = true
- RADIUS: BLOCKED profile

**After Payment:**
- Invoice #1: PAID
- No other overdue invoices
- Customer: blocked = false (automatically)
- RADIUS: Full bandwidth restored on next authentication

### Scenario 3: Customer with Multiple Overdue Invoices

**Before Payment:**
- Invoice #1: OVERDUE (current month)
- Invoice #2: OVERDUE (previous month)
- Customer: blocked = true

**After Paying Invoice #1:**
- Invoice #1: PAID
- Invoice #2: OVERDUE (still exists)
- Customer: blocked = true (remains blocked)
- RADIUS: BLOCKED profile (still restricted)

**After Paying Invoice #2:**
- Invoice #1: PAID
- Invoice #2: PAID
- Customer: blocked = false (automatically unblocked)
- RADIUS: Full bandwidth restored

## Configuration

### Application Properties

```yaml
# application.yml

# Schedule configuration (optional, default is 2 AM daily)
delinquency:
  check:
    cron: "0 0 2 * * *"  # Daily at 2 AM

# RADIUS configuration
radius:
  mikrotik-rate-limit-attribute: "Mikrotik-Rate-Limit"
```

### Enable Scheduling

Already enabled in `Application.java`:
```java
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling  // Required for @Scheduled tasks
public class Application {
    // ...
}
```

## Testing

### Unit Tests

**DelinquencyServiceTest** (8 test cases):
- Block customer with overdue invoice
- Don't block already blocked customer
- Handle multiple overdue invoices
- Handle customer not found
- Handle no overdue invoices
- Detect customer has overdue invoices
- Detect customer has no overdue invoices
- Handle invalid customer ID format

**BillingServiceTest** (6 test cases):
- Unblock customer after paying last invoice
- Keep customer blocked if other overdue invoices exist
- Handle customer not found during unblock
- Throw exception when invoice not found
- Generate invoice with PENDING status
- List invoices by tenant

**RadiusServerServiceTest** (8 test cases):
- Authenticate active customer with full bandwidth
- Return BLOCKED profile for blocked customer
- Reject authentication for non-existent customer
- Reject authentication for inactive customer
- Find customer by document number
- Return correct bandwidth for different plans
- Handle authentication errors gracefully

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=DelinquencyServiceTest

# Run tests with coverage
mvn test jacoco:report
```

### Manual Testing

1. **Create test customer and invoice:**
```bash
# Create customer
POST /customers
{
  "fullName": "Test Customer",
  "document": "12345678900",
  "plan": "BASIC"
}

# Create overdue invoice
POST /billing/invoices/generate
{
  "customerId": "{customer-id}",
  "amount": 100.00,
  "dueDate": "2024-01-01"  # Past date
}
```

2. **Trigger delinquency check manually:**
```bash
# Call manual delinquency check (if endpoint exposed)
POST /admin/delinquency/check
```

3. **Verify customer is blocked:**
```bash
GET /customers/{customer-id}
# Response should show: "blocked": true
```

4. **Test RADIUS authentication:**
```bash
# Attempt PPPoE authentication
# Should return BLOCKED profile with 125/125 bandwidth
```

5. **Pay invoice and verify unblock:**
```bash
POST /billing/pay/{invoice-id}

# Verify customer is unblocked
GET /customers/{customer-id}
# Response should show: "blocked": false

# Test RADIUS authentication again
# Should return full bandwidth
```

## Monitoring and Logging

### Log Messages

**Delinquency Check:**
```
INFO: Starting delinquency check for overdue invoices
INFO: Found X overdue invoices
INFO: Blocked customer {id} due to overdue invoice {invoice-id}
INFO: Delinquency check completed. Invoices marked as OVERDUE: X, Customers blocked: Y
```

**Payment and Unblocking:**
```
INFO: Invoice {id} marked as PAID
INFO: Customer {id} automatically unblocked after payment
INFO: Customer {id} still has overdue invoices, remaining blocked
```

**RADIUS Authentication:**
```
INFO: RADIUS authentication request for user: {username}
WARN: Customer {username} is blocked due to delinquency
INFO: RADIUS authentication successful for customer: {username} with plan: {plan}
```

### Metrics to Monitor

- Number of blocked customers per day
- Number of overdue invoices
- Average time to payment after blocking
- Number of automatic unblocks per day
- Failed authentication attempts due to blocking

## Security Considerations

1. **Minimal Bandwidth**: Blocked customers get 1 Kbps, not complete disconnection
   - Allows access to payment portal
   - Prevents complete service disruption
   - Encourages payment while maintaining pressure

2. **Automatic Unblocking**: Happens only after all overdue invoices are paid
   - Prevents gaming the system
   - Ensures complete payment before restoration

3. **Multi-tenant Safety**: All operations respect tenant boundaries
   - Delinquency checks are tenant-aware
   - RADIUS authentication enforces tenant context

4. **Audit Trail**: All blocking/unblocking actions are logged
   - Can be integrated with AuditLogService
   - Provides compliance and troubleshooting capability

## Future Enhancements

1. **Configurable Grace Period**: Allow X days after due date before blocking
2. **Notification System**: Send email/SMS before and after blocking
3. **Partial Payment Handling**: Unblock based on payment percentage
4. **Custom Blocked Bandwidth**: Allow ISP to configure blocked bandwidth per plan
5. **Redirect to Payment Portal**: Instead of throttling, redirect to payment page
6. **Manual Override**: Admin interface to manually block/unblock customers
7. **Progressive Throttling**: Gradually reduce bandwidth as days overdue increase

## Troubleshooting

### Customer not blocked after overdue invoice

**Check:**
1. Is scheduling enabled? (`@EnableScheduling` in Application.java)
2. Has the scheduled task run? (Check logs for "Starting delinquency check")
3. Is the invoice status PENDING? (Only PENDING invoices are checked)
4. Is the due date in the past? (Check `invoice.dueDate < LocalDate.now()`)

### Customer not unblocked after payment

**Check:**
1. Are there other overdue invoices? (`hasOverdueInvoices()` returns true)
2. Was the payment successful? (Invoice status changed to PAID)
3. Check logs for "Customer X automatically unblocked" or "still has overdue invoices"

### RADIUS returning wrong profile

**Check:**
1. Is customer.blocked flag set correctly in database?
2. Is RadiusServerService checking the blocked flag?
3. Check RADIUS logs for "Customer {username} is blocked"
4. Verify MikroTik is receiving correct Rate-Limit attribute

## Acceptance Criteria (from Issue)

- ✅ **Cliente bloqueado sem ação manual**: Automatic blocking via scheduled task
- ✅ **Liberação imediata após pagamento**: Automatic unblocking in payment flow
- ✅ **Perfil BLOQUEADO no RADIUS**: BLOCKED profile with 125 bytes/sec
- ✅ **Banda mínima ou redirect**: Minimal 1 Kbps bandwidth for blocked customers

## Summary

The automatic customer blocking for delinquency feature is now fully implemented with:
- Scheduled daily checks for overdue invoices
- Automatic blocking of delinquent customers
- BLOCKED RADIUS profile with minimal bandwidth
- Automatic unblocking upon payment
- Comprehensive unit tests
- Full integration with existing billing and RADIUS systems

The implementation follows best practices for multi-tenant ISP platforms and provides a foundation for future enhancements.
