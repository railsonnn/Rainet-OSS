package com.isp.platform.provisioning.radius;

import com.isp.platform.customer.domain.Customer;
import com.isp.platform.customer.domain.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RadiusServerService.
 * Tests RADIUS authentication and blocked customer handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RadiusServerService Tests")
class RadiusServerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private RadiusServerService radiusServerService;

    private Customer activeCustomer;
    private Customer blockedCustomer;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        
        activeCustomer = new Customer();
        activeCustomer.setId(customerId);
        activeCustomer.setFullName("Active Customer");
        activeCustomer.setDocument("12345678900");
        activeCustomer.setPlan("BASIC");
        activeCustomer.setStatus("ACTIVE");
        activeCustomer.setBlocked(false);

        blockedCustomer = new Customer();
        blockedCustomer.setId(customerId);
        blockedCustomer.setFullName("Blocked Customer");
        blockedCustomer.setDocument("98765432100");
        blockedCustomer.setPlan("BASIC");
        blockedCustomer.setStatus("ACTIVE");
        blockedCustomer.setBlocked(true);
        
        // Set the rate limit attribute value
        ReflectionTestUtils.setField(radiusServerService, "rateLimitAttribute", "Mikrotik-Rate-Limit");
    }

    @Test
    @DisplayName("Should authenticate active customer with full bandwidth")
    void shouldAuthenticateActiveCustomerWithFullBandwidth() {
        // Given
        RadiusAuthRequest request = RadiusAuthRequest.builder()
                .username(customerId.toString())
                .password("password123")
                .build();
        
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(activeCustomer));

        // When
        RadiusAuthRequest.RadiusAuthResponse response = radiusServerService.authenticate(request);

        // Then
        assertTrue(response.isAuthenticated());
        assertEquals("BASIC", response.getProfileName());
        assertTrue(response.getUploadMbps() > 0);
        assertTrue(response.getDownloadMbps() > 0);
        assertNotNull(response.getAttributes());
        assertTrue(response.getAttributes().containsKey("Mikrotik-Rate-Limit"));
    }

    @Test
    @DisplayName("Should return blocked profile for blocked customer")
    void shouldReturnBlockedProfileForBlockedCustomer() {
        // Given
        RadiusAuthRequest request = RadiusAuthRequest.builder()
                .username(customerId.toString())
                .password("password123")
                .build();
        
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(blockedCustomer));

        // When
        RadiusAuthRequest.RadiusAuthResponse response = radiusServerService.authenticate(request);

        // Then
        assertTrue(response.isAuthenticated()); // Still authenticated but restricted
        assertEquals("BLOCKED", response.getProfileName());
        assertEquals(0, response.getUploadMbps());
        assertEquals(0, response.getDownloadMbps());
        assertNotNull(response.getAttributes());
        assertEquals("125/125", response.getAttributes().get("Mikrotik-Rate-Limit")); // 1 Kbps
        assertTrue(response.getAttributes().get("Reply-Message").contains("blocked"));
    }

    @Test
    @DisplayName("Should reject authentication for non-existent customer")
    void shouldRejectAuthenticationForNonExistentCustomer() {
        // Given
        RadiusAuthRequest request = RadiusAuthRequest.builder()
                .username("nonexistent")
                .password("password123")
                .build();
        
        when(customerRepository.findById(any(UUID.class)))
                .thenReturn(Optional.empty());
        when(customerRepository.findByDocument("nonexistent"))
                .thenReturn(Optional.empty());

        // When
        RadiusAuthRequest.RadiusAuthResponse response = radiusServerService.authenticate(request);

        // Then
        assertFalse(response.isAuthenticated());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should reject authentication for inactive customer")
    void shouldRejectAuthenticationForInactiveCustomer() {
        // Given
        activeCustomer.setStatus("SUSPENDED");
        RadiusAuthRequest request = RadiusAuthRequest.builder()
                .username(customerId.toString())
                .password("password123")
                .build();
        
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(activeCustomer));

        // When
        RadiusAuthRequest.RadiusAuthResponse response = radiusServerService.authenticate(request);

        // Then
        assertFalse(response.isAuthenticated());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("not active"));
    }

    @Test
    @DisplayName("Should find customer by document number")
    void shouldFindCustomerByDocumentNumber() {
        // Given
        String document = "12345678900";
        activeCustomer.setDocument(document);
        RadiusAuthRequest request = RadiusAuthRequest.builder()
                .username(document)
                .password("password123")
                .build();
        
        when(customerRepository.findById(any(UUID.class)))
                .thenReturn(Optional.empty());
        when(customerRepository.findByDocument(document))
                .thenReturn(Optional.of(activeCustomer));

        // When
        RadiusAuthRequest.RadiusAuthResponse response = radiusServerService.authenticate(request);

        // Then
        assertTrue(response.isAuthenticated());
        assertEquals("BASIC", response.getProfileName());
    }

    @Test
    @DisplayName("Should return correct bandwidth for different plans")
    void shouldReturnCorrectBandwidthForDifferentPlans() {
        // Test STANDARD plan
        activeCustomer.setPlan("STANDARD");
        RadiusAuthRequest request = RadiusAuthRequest.builder()
                .username(customerId.toString())
                .password("password123")
                .build();
        
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(activeCustomer));

        RadiusAuthRequest.RadiusAuthResponse response = radiusServerService.authenticate(request);

        assertTrue(response.isAuthenticated());
        assertEquals("STANDARD", response.getProfileName());
        assertEquals(10, response.getUploadMbps());
        assertEquals(20, response.getDownloadMbps());
    }

    @Test
    @DisplayName("Should handle authentication errors gracefully")
    void shouldHandleAuthenticationErrorsGracefully() {
        // Given
        RadiusAuthRequest request = RadiusAuthRequest.builder()
                .username(customerId.toString())
                .password("password123")
                .build();
        
        when(customerRepository.findById(customerId))
                .thenThrow(new RuntimeException("Database error"));

        // When
        RadiusAuthRequest.RadiusAuthResponse response = radiusServerService.authenticate(request);

        // Then
        assertFalse(response.isAuthenticated());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("error"));
    }
}
