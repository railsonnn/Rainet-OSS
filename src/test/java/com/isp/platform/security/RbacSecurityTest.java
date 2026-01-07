package com.isp.platform.security;

import com.isp.platform.admin.controller.AdminController;
import com.isp.platform.admin.service.AdminService;
import com.isp.platform.admin.service.PopRequest;
import com.isp.platform.admin.service.RouterRequest;
import com.isp.platform.billing.controller.BillingController;
import com.isp.platform.billing.service.BillingService;
import com.isp.platform.customer.controller.CustomerController;
import com.isp.platform.customer.controller.CustomerPortalController;
import com.isp.platform.customer.service.CustomerService;
import com.isp.platform.provisioning.controller.ProvisioningController;
import com.isp.platform.provisioning.service.ProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * RBAC Security Tests
 * 
 * Validates that role-based access control is properly enforced across all endpoints.
 * Tests ensure that users can only access endpoints appropriate for their roles.
 */
@WebMvcTest(controllers = {
    AdminController.class,
    ProvisioningController.class,
    BillingController.class,
    CustomerController.class,
    CustomerPortalController.class
})
@DisplayName("RBAC Security Tests")
public class RbacSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private ProvisioningService provisioningService;

    @MockBean
    private BillingService billingService;

    @MockBean
    private CustomerService customerService;

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TEST_TENANT_ID = "550e8400-e29b-41d4-a716-446655440000";

    // ========== ADMIN ENDPOINT TESTS ==========

    @Test
    @DisplayName("ADMIN role can access admin endpoints")
    @WithMockUser(roles = "ADMIN")
    public void testAdminRoleCanAccessAdminEndpoints() throws Exception {
        String popJson = "{\"name\":\"POP-01\",\"city\":\"São Paulo\"}";
        
        mockMvc.perform(post("/admin/pops")
                .header(TENANT_HEADER, TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(popJson)
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TECH role can access admin endpoints")
    @WithMockUser(roles = "TECH")
    public void testTechRoleCanAccessAdminEndpoints() throws Exception {
        String popJson = "{\"name\":\"POP-01\",\"city\":\"São Paulo\"}";
        
        mockMvc.perform(post("/admin/pops")
                .header(TENANT_HEADER, TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(popJson)
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FINANCE role cannot access admin endpoints")
    @WithMockUser(roles = "FINANCE")
    public void testFinanceRoleCannotAccessAdminEndpoints() throws Exception {
        String popJson = "{\"name\":\"POP-01\",\"city\":\"São Paulo\"}";
        
        mockMvc.perform(post("/admin/pops")
                .header(TENANT_HEADER, TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(popJson)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPPORT role cannot access admin endpoints")
    @WithMockUser(roles = "SUPPORT")
    public void testSupportRoleCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/routers")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CUSTOMER role cannot access admin endpoints")
    @WithMockUser(roles = "CUSTOMER")
    public void testCustomerRoleCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/routers")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isForbidden());
    }

    // ========== PROVISIONING ENDPOINT TESTS ==========

    @Test
    @DisplayName("ADMIN role can access provisioning endpoints")
    @WithMockUser(roles = "ADMIN")
    public void testAdminRoleCanAccessProvisioningEndpoints() throws Exception {
        mockMvc.perform(get("/provisioning/snapshots")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TECH role can access provisioning endpoints")
    @WithMockUser(roles = "TECH")
    public void testTechRoleCanAccessProvisioningEndpoints() throws Exception {
        mockMvc.perform(get("/provisioning/snapshots")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FINANCE role cannot access provisioning endpoints")
    @WithMockUser(roles = "FINANCE")
    public void testFinanceRoleCannotAccessProvisioningEndpoints() throws Exception {
        mockMvc.perform(get("/provisioning/snapshots")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isForbidden());
    }

    // ========== BILLING ENDPOINT TESTS ==========

    @Test
    @DisplayName("ADMIN role can generate invoices")
    @WithMockUser(roles = "ADMIN")
    public void testAdminRoleCanGenerateInvoices() throws Exception {
        String invoiceJson = "{\"customerId\":\"" + UUID.randomUUID() + "\",\"amount\":100.0,\"dueDate\":\"2026-02-01\"}";
        
        mockMvc.perform(post("/billing/invoices/generate")
                .header(TENANT_HEADER, TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invoiceJson)
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FINANCE role can generate invoices")
    @WithMockUser(roles = "FINANCE")
    public void testFinanceRoleCanGenerateInvoices() throws Exception {
        String invoiceJson = "{\"customerId\":\"" + UUID.randomUUID() + "\",\"amount\":100.0,\"dueDate\":\"2026-02-01\"}";
        
        mockMvc.perform(post("/billing/invoices/generate")
                .header(TENANT_HEADER, TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invoiceJson)
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPPORT role can list invoices but cannot generate")
    @WithMockUser(roles = "SUPPORT")
    public void testSupportRoleCanListButNotGenerateInvoices() throws Exception {
        // Can list
        mockMvc.perform(get("/billing/invoices")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isOk());
        
        // Cannot generate
        String invoiceJson = "{\"customerId\":\"" + UUID.randomUUID() + "\",\"amount\":100.0,\"dueDate\":\"2026-02-01\"}";
        mockMvc.perform(post("/billing/invoices/generate")
                .header(TENANT_HEADER, TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invoiceJson)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TECH role cannot access billing endpoints")
    @WithMockUser(roles = "TECH")
    public void testTechRoleCannotAccessBillingEndpoints() throws Exception {
        mockMvc.perform(get("/billing/invoices")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isForbidden());
    }

    // ========== CUSTOMER ENDPOINT TESTS ==========

    @Test
    @DisplayName("ADMIN role can access customer endpoints")
    @WithMockUser(roles = "ADMIN")
    public void testAdminRoleCanAccessCustomerEndpoints() throws Exception {
        mockMvc.perform(get("/customers/" + UUID.randomUUID())
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPPORT role can create and view customers")
    @WithMockUser(roles = "SUPPORT")
    public void testSupportRoleCanCreateAndViewCustomers() throws Exception {
        String customerJson = "{\"fullName\":\"John Doe\",\"document\":\"12345678900\",\"plan\":\"BASIC\"}";
        
        mockMvc.perform(post("/customers")
                .header(TENANT_HEADER, TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(customerJson)
                .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/customers/" + UUID.randomUUID())
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CUSTOMER role can view customers but not create")
    @WithMockUser(roles = "CUSTOMER")
    public void testCustomerRoleCanViewButNotCreate() throws Exception {
        // Can view
        mockMvc.perform(get("/customers/" + UUID.randomUUID())
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isOk());
        
        // Cannot create
        String customerJson = "{\"fullName\":\"John Doe\",\"document\":\"12345678900\",\"plan\":\"BASIC\"}";
        mockMvc.perform(post("/customers")
                .header(TENANT_HEADER, TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(customerJson)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FINANCE role cannot access customer management endpoints")
    @WithMockUser(roles = "FINANCE")
    public void testFinanceRoleCannotAccessCustomerEndpoints() throws Exception {
        mockMvc.perform(get("/customers/" + UUID.randomUUID())
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isForbidden());
    }

    // ========== CUSTOMER PORTAL TESTS ==========

    @Test
    @DisplayName("CUSTOMER role can access customer portal")
    @WithMockUser(roles = "CUSTOMER")
    public void testCustomerRoleCanAccessCustomerPortal() throws Exception {
        mockMvc.perform(get("/customer/dashboard")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isOk());

        mockMvc.perform(post("/customer/unlock")
                .header(TENANT_HEADER, TEST_TENANT_ID)
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN role cannot access customer portal endpoints")
    @WithMockUser(roles = "ADMIN")
    public void testAdminRoleCannotAccessCustomerPortal() throws Exception {
        mockMvc.perform(get("/customer/dashboard")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPPORT role cannot access customer portal endpoints")
    @WithMockUser(roles = "SUPPORT")
    public void testSupportRoleCannotAccessCustomerPortal() throws Exception {
        mockMvc.perform(get("/customer/dashboard")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isForbidden());
    }

    // ========== UNAUTHENTICATED ACCESS TESTS ==========

    @Test
    @DisplayName("Unauthenticated users cannot access protected endpoints")
    public void testUnauthenticatedAccessDenied() throws Exception {
        mockMvc.perform(get("/admin/routers")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/billing/invoices")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/customer/dashboard")
                .header(TENANT_HEADER, TEST_TENANT_ID))
                .andExpect(status().isUnauthorized());
    }
}
