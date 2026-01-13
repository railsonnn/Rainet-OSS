package com.isp.platform.security;

import com.isp.platform.billing.domain.Invoice;
import com.isp.platform.billing.domain.InvoiceRepository;
import com.isp.platform.billing.domain.InvoiceStatus;
import com.isp.platform.billing.service.BillingService;
import com.isp.platform.billing.service.GenerateInvoiceRequest;
import com.isp.platform.common.exception.ApiException;
import com.isp.platform.customer.domain.Customer;
import com.isp.platform.customer.domain.CustomerRepository;
import com.isp.platform.customer.service.CustomerRequest;
import com.isp.platform.customer.service.CustomerService;
import com.isp.platform.gateway.tenant.TenantContext;
import com.isp.platform.provisioning.domain.Pop;
import com.isp.platform.provisioning.domain.PopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-Tenant Isolation Tests
 * 
 * Validates that tenant isolation is properly enforced at the service and data layer.
 * Tests ensure no data leakage between different tenants.
 */
@SpringBootTest
@DisplayName("Multi-Tenant Isolation Tests")
public class MultiTenantIsolationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BillingService billingService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PopRepository popRepository;

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private Customer customerA;
    private Customer customerB;
    private Invoice invoiceA;
    private Invoice invoiceB;

    @BeforeEach
    public void setUp() {
        // Clean up before each test
        customerRepository.deleteAll();
        invoiceRepository.deleteAll();
        popRepository.deleteAll();
        
        // Create test data for Tenant A
        TenantContext.setCurrentTenant(TENANT_A);
        customerA = new Customer();
        customerA.setFullName("Customer A");
        customerA.setDocument("11111111111");
        customerA.setPlan("BASIC");
        customerA.setStatus("ACTIVE");
        customerA.setTenantId(TENANT_A);
        customerA = customerRepository.save(customerA);

        invoiceA = new Invoice();
        invoiceA.setCustomerId(customerA.getId());
        invoiceA.setAmount(BigDecimal.valueOf(100.00));
        invoiceA.setDueDate(LocalDate.now().plusDays(30));
        invoiceA.setStatus(InvoiceStatus.PENDING);
        invoiceA.setTenantId(TENANT_A);
        invoiceA = invoiceRepository.save(invoiceA);

        // Create test data for Tenant B
        TenantContext.setCurrentTenant(TENANT_B);
        customerB = new Customer();
        customerB.setFullName("Customer B");
        customerB.setDocument("22222222222");
        customerB.setPlan("PREMIUM");
        customerB.setStatus("ACTIVE");
        customerB.setTenantId(TENANT_B);
        customerB = customerRepository.save(customerB);

        invoiceB = new Invoice();
        invoiceB.setCustomerId(customerB.getId());
        invoiceB.setAmount(BigDecimal.valueOf(200.00));
        invoiceB.setDueDate(LocalDate.now().plusDays(30));
        invoiceB.setStatus(InvoiceStatus.PENDING);
        invoiceB.setTenantId(TENANT_B);
        invoiceB = invoiceRepository.save(invoiceB);
    }

    @AfterEach
    public void tearDown() {
        TenantContext.clear();
    }

    // ========== CUSTOMER SERVICE ISOLATION TESTS ==========

    @Test
    @DisplayName("Tenant A cannot access Tenant B's customers")
    public void testTenantACannotAccessTenantBCustomers() {
        TenantContext.setCurrentTenant(TENANT_A);
        
        // Tenant A should be able to access their own customer
        Customer retrieved = customerService.find(customerA.getId());
        assertNotNull(retrieved);
        assertEquals("Customer A", retrieved.getFullName());
        
        // Tenant A should NOT be able to access Tenant B's customer
        assertThrows(ApiException.class, () -> {
            customerService.find(customerB.getId());
        });
    }

    @Test
    @DisplayName("Tenant B cannot access Tenant A's customers")
    public void testTenantBCannotAccessTenantACustomers() {
        TenantContext.setCurrentTenant(TENANT_B);
        
        // Tenant B should be able to access their own customer
        Customer retrieved = customerService.find(customerB.getId());
        assertNotNull(retrieved);
        assertEquals("Customer B", retrieved.getFullName());
        
        // Tenant B should NOT be able to access Tenant A's customer
        assertThrows(ApiException.class, () -> {
            customerService.find(customerA.getId());
        });
    }

    @Test
    @DisplayName("Creating customer without tenant context should fail")
    public void testCreateCustomerWithoutTenantContextFails() {
        TenantContext.clear();
        
        CustomerRequest request = new CustomerRequest(
            "Test Customer",
            "33333333333",
            "BASIC"
        );
        
        assertThrows(ApiException.class, () -> {
            customerService.create(request);
        });
    }

    // ========== BILLING SERVICE ISOLATION TESTS ==========

    @Test
    @DisplayName("Tenant A can only list their own invoices")
    public void testTenantACanOnlyListOwnInvoices() {
        TenantContext.setCurrentTenant(TENANT_A);
        
        List<Invoice> invoices = billingService.list();
        
        assertNotNull(invoices);
        assertEquals(1, invoices.size());
        assertEquals(invoiceA.getId(), invoices.get(0).getId());
        assertEquals(BigDecimal.valueOf(100.00), invoices.get(0).getAmount());
    }

    @Test
    @DisplayName("Tenant B can only list their own invoices")
    public void testTenantBCanOnlyListOwnInvoices() {
        TenantContext.setCurrentTenant(TENANT_B);
        
        List<Invoice> invoices = billingService.list();
        
        assertNotNull(invoices);
        assertEquals(1, invoices.size());
        assertEquals(invoiceB.getId(), invoices.get(0).getId());
        assertEquals(BigDecimal.valueOf(200.00), invoices.get(0).getAmount());
    }

    @Test
    @DisplayName("Tenant A cannot pay Tenant B's invoice")
    public void testTenantACannotPayTenantBInvoice() {
        TenantContext.setCurrentTenant(TENANT_A);
        
        assertThrows(ApiException.class, () -> {
            billingService.pay(invoiceB.getId(), null);
        });
    }

    @Test
    @DisplayName("Tenant B cannot pay Tenant A's invoice")
    public void testTenantBCannotPayTenantAInvoice() {
        TenantContext.setCurrentTenant(TENANT_B);
        
        assertThrows(ApiException.class, () -> {
            billingService.pay(invoiceA.getId(), null);
        });
    }

    @Test
    @DisplayName("Creating invoice without tenant context should fail")
    public void testCreateInvoiceWithoutTenantContextFails() {
        TenantContext.clear();
        
        GenerateInvoiceRequest request = new GenerateInvoiceRequest(
            UUID.randomUUID(),
            BigDecimal.valueOf(50.00),
            LocalDate.now().plusDays(15)
        );
        
        assertThrows(ApiException.class, () -> {
            billingService.generate(request);
        });
    }

    // ========== REPOSITORY LEVEL ISOLATION TESTS ==========

    @Test
    @DisplayName("Repository queries filter by tenant ID")
    public void testRepositoryQueriesFilterByTenantId() {
        // Verify Tenant A's data
        List<Customer> tenantACustomers = customerRepository.findByTenantId(TENANT_A);
        assertEquals(1, tenantACustomers.size());
        assertEquals("Customer A", tenantACustomers.get(0).getFullName());
        
        // Verify Tenant B's data
        List<Customer> tenantBCustomers = customerRepository.findByTenantId(TENANT_B);
        assertEquals(1, tenantBCustomers.size());
        assertEquals("Customer B", tenantBCustomers.get(0).getFullName());
    }

    @Test
    @DisplayName("Repository findByIdAndTenantId enforces isolation")
    public void testRepositoryFindByIdAndTenantIdEnforcesIsolation() {
        // Tenant A can find their own customer
        assertTrue(customerRepository.findByIdAndTenantId(customerA.getId(), TENANT_A).isPresent());
        
        // Tenant A cannot find Tenant B's customer
        assertFalse(customerRepository.findByIdAndTenantId(customerB.getId(), TENANT_A).isPresent());
        
        // Tenant B can find their own customer
        assertTrue(customerRepository.findByIdAndTenantId(customerB.getId(), TENANT_B).isPresent());
        
        // Tenant B cannot find Tenant A's customer
        assertFalse(customerRepository.findByIdAndTenantId(customerA.getId(), TENANT_B).isPresent());
    }

    // ========== TENANT CONTEXT TESTS ==========

    @Test
    @DisplayName("Tenant context is properly isolated per thread")
    public void testTenantContextThreadIsolation() {
        TenantContext.setCurrentTenant(TENANT_A);
        assertEquals(TENANT_A, TenantContext.getCurrentTenant());
        
        TenantContext.setCurrentTenant(TENANT_B);
        assertEquals(TENANT_B, TenantContext.getCurrentTenant());
        
        TenantContext.clear();
        assertNull(TenantContext.getCurrentTenant());
    }

    @Test
    @DisplayName("Operations require tenant context to be set")
    public void testOperationsRequireTenantContext() {
        TenantContext.clear();
        
        // Should fail when accessing customer without tenant context
        assertThrows(ApiException.class, () -> {
            customerService.find(customerA.getId());
        });
        
        // Should fail when listing invoices without tenant context
        assertThrows(ApiException.class, () -> {
            billingService.list();
        });
    }

    @Test
    @DisplayName("Entity listener prevents saving without tenant context")
    @Transactional
    public void testEntityListenerPreventsSavingWithoutTenantContext() {
        TenantContext.clear();
        
        Customer customer = new Customer();
        customer.setFullName("Test Customer");
        customer.setDocument("44444444444");
        customer.setPlan("BASIC");
        customer.setStatus("ACTIVE");
        
        // Should throw exception because tenant context is not set
        assertThrows(IllegalStateException.class, () -> {
            customerRepository.save(customer);
        });
    }

    @Test
    @DisplayName("Multiple tenants can have data with same business keys")
    public void testMultipleTenantsCanHaveSameBusinessKeys() {
        // Both tenants can have POPs with the same name
        TenantContext.setCurrentTenant(TENANT_A);
        Pop popA = new Pop();
        popA.setName("Main POP");
        popA.setCity("São Paulo");
        popA.setTenantId(TENANT_A);
        popA = popRepository.save(popA);

        TenantContext.setCurrentTenant(TENANT_B);
        Pop popB = new Pop();
        popB.setName("Main POP");  // Same name as Tenant A's POP
        popB.setCity("Rio de Janeiro");
        popB.setTenantId(TENANT_B);
        popB = popRepository.save(popB);

        // Verify both POPs exist but are isolated
        assertEquals(1, popRepository.findByTenantId(TENANT_A).size());
        assertEquals(1, popRepository.findByTenantId(TENANT_B).size());
        
        // Each tenant sees only their own POP
        TenantContext.setCurrentTenant(TENANT_A);
        List<Pop> tenant_a_pops = popRepository.findByTenantId(TENANT_A);
        assertEquals("São Paulo", tenant_a_pops.get(0).getCity());

        TenantContext.setCurrentTenant(TENANT_B);
        List<Pop> tenant_b_pops = popRepository.findByTenantId(TENANT_B);
        assertEquals("Rio de Janeiro", tenant_b_pops.get(0).getCity());
    }
}
