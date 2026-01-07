package com.isp.platform.provisioning.service;

import com.isp.platform.audit.domain.AuditLog;
import com.isp.platform.audit.service.AuditService;
import com.isp.platform.common.exception.ApiException;
import com.isp.platform.gateway.tenant.TenantContext;
import com.isp.platform.provisioning.domain.Router;
import com.isp.platform.provisioning.domain.RouterRepository;
import com.isp.platform.provisioning.mikrotik.RouterOsExecutor;
import com.isp.platform.provisioning.mikrotik.RouterOsScriptGenerator;
import com.isp.platform.provisioning.snapshot.ConfigSnapshot;
import com.isp.platform.provisioning.snapshot.ConfigSnapshotRepository;
import com.isp.platform.provisioning.snapshot.ConfigSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Provisioning Service - Rollback Configuration Tests")
class ProvisioningServiceRollbackTest {

    @Mock
    private RouterRepository routerRepository;

    @Mock
    private RouterOsScriptGenerator scriptGenerator;

    @Mock
    private RouterOsExecutor executor;

    @Mock
    private ConfigSnapshotRepository snapshotRepository;

    @Mock
    private ConfigSnapshotService snapshotService;

    @Mock
    private AuditService auditService;

    private ProvisioningService provisioningService;

    private UUID tenantId;
    private UUID routerId;
    private Router router;

    @BeforeEach
    void setUp() {
        provisioningService = new ProvisioningService(
                routerRepository,
                scriptGenerator,
                executor,
                snapshotRepository,
                snapshotService,
                auditService
        );

        tenantId = UUID.randomUUID();
        routerId = UUID.randomUUID();

        router = new Router();
        router.setId(routerId);
        router.setTenantId(tenantId);
        router.setHostname("test-router");
        router.setManagementAddress("192.168.1.1");
        router.setApiUsername("admin");
        router.setApiPassword("password");

        // Set tenant context
        TenantContext.setCurrentTenant(tenantId);
    }

    @Test
    @DisplayName("Apply configuration should create BEFORE and AFTER snapshots")
    void testApplyCreatesBeforeAndAfterSnapshots() {
        // Arrange
        UUID beforeSnapshotId = UUID.randomUUID();
        UUID afterSnapshotId = UUID.randomUUID();
        String script = "/system identity set name=test-router";
        String actor = "admin@test.com";
        String description = "Initial configuration";

        ConfigSnapshot beforeSnapshot = new ConfigSnapshot();
        beforeSnapshot.setId(beforeSnapshotId);
        beforeSnapshot.setSnapshotType(ConfigSnapshot.SnapshotType.BEFORE);

        ConfigSnapshot afterSnapshot = new ConfigSnapshot();
        afterSnapshot.setId(afterSnapshotId);
        afterSnapshot.setSnapshotType(ConfigSnapshot.SnapshotType.AFTER);

        ProvisioningRequest request = new ProvisioningRequest(routerId, description);

        when(routerRepository.findByIdAndTenantId(routerId, tenantId)).thenReturn(Optional.of(router));
        when(scriptGenerator.generateProvisioningScript(router)).thenReturn(script);
        when(snapshotService.createBeforeSnapshot(router, actor)).thenReturn(beforeSnapshot);
        when(snapshotRepository.save(any(ConfigSnapshot.class))).thenReturn(afterSnapshot);

        // Act
        UUID resultSnapshotId = provisioningService.apply(request, actor);

        // Assert
        assertEquals(afterSnapshotId, resultSnapshotId);
        verify(snapshotService).createBeforeSnapshot(router, actor);
        verify(executor).applyScript(router, script);
        verify(snapshotRepository).save(any(ConfigSnapshot.class));
        verify(auditService).record(
                eq(actor),
                eq(AuditLog.AuditAction.PROVISIONING_APPLY),
                eq("Router"),
                eq(routerId.toString()),
                anyString()
        );
    }

    @Test
    @DisplayName("Rollback should only accept BEFORE snapshots")
    void testRollbackOnlyAcceptsBeforeSnapshots() {
        // Arrange
        UUID snapshotId = UUID.randomUUID();
        String actor = "admin@test.com";

        ConfigSnapshot afterSnapshot = new ConfigSnapshot();
        afterSnapshot.setId(snapshotId);
        afterSnapshot.setTenantId(tenantId);
        afterSnapshot.setSnapshotType(ConfigSnapshot.SnapshotType.AFTER);
        afterSnapshot.setRouter(router);

        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(afterSnapshot));

        // Act & Assert
        ApiException exception = assertThrows(ApiException.class, () -> {
            provisioningService.rollback(snapshotId, actor);
        });

        assertTrue(exception.getMessage().contains("BEFORE snapshots"));
        verify(executor, never()).applyScript(any(), anyString());
    }

    @Test
    @DisplayName("Rollback should restore configuration from BEFORE snapshot")
    void testRollbackRestoresConfiguration() {
        // Arrange
        UUID snapshotId = UUID.randomUUID();
        String actor = "admin@test.com";
        String beforeConfig = "/system identity set name=original-config";

        ConfigSnapshot beforeSnapshot = new ConfigSnapshot();
        beforeSnapshot.setId(snapshotId);
        beforeSnapshot.setTenantId(tenantId);
        beforeSnapshot.setSnapshotType(ConfigSnapshot.SnapshotType.BEFORE);
        beforeSnapshot.setRouter(router);
        beforeSnapshot.setConfigScript(beforeConfig);
        beforeSnapshot.setConfigHash("abc123");

        ConfigSnapshot afterSnapshot = new ConfigSnapshot();
        afterSnapshot.setId(UUID.randomUUID());
        afterSnapshot.setSnapshotType(ConfigSnapshot.SnapshotType.AFTER);

        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(beforeSnapshot));
        when(snapshotService.verifySnapshot(beforeSnapshot)).thenReturn(true);
        when(snapshotRepository.save(any(ConfigSnapshot.class))).thenReturn(afterSnapshot);

        // Act
        provisioningService.rollback(snapshotId, actor);

        // Assert
        verify(snapshotService).verifySnapshot(beforeSnapshot);
        verify(executor).applyScript(router, beforeConfig);
        
        ArgumentCaptor<ConfigSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ConfigSnapshot.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        
        ConfigSnapshot savedSnapshot = snapshotCaptor.getValue();
        assertEquals(ConfigSnapshot.SnapshotType.AFTER, savedSnapshot.getSnapshotType());
        assertTrue(savedSnapshot.getDescription().contains("Rollback"));
        
        verify(auditService).record(
                eq(actor),
                eq(AuditLog.AuditAction.PROVISIONING_ROLLBACK),
                eq("Router"),
                eq(routerId.toString()),
                anyString()
        );
    }

    @Test
    @DisplayName("Rollback should fail if snapshot integrity verification fails")
    void testRollbackFailsOnIntegrityCheckFailure() {
        // Arrange
        UUID snapshotId = UUID.randomUUID();
        String actor = "admin@test.com";

        ConfigSnapshot beforeSnapshot = new ConfigSnapshot();
        beforeSnapshot.setId(snapshotId);
        beforeSnapshot.setTenantId(tenantId);
        beforeSnapshot.setSnapshotType(ConfigSnapshot.SnapshotType.BEFORE);
        beforeSnapshot.setRouter(router);
        beforeSnapshot.setConfigScript("/system identity set name=test");
        beforeSnapshot.setConfigHash("invalid-hash");

        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(beforeSnapshot));
        when(snapshotService.verifySnapshot(beforeSnapshot)).thenReturn(false);

        // Act & Assert
        ApiException exception = assertThrows(ApiException.class, () -> {
            provisioningService.rollback(snapshotId, actor);
        });

        assertTrue(exception.getMessage().contains("integrity verification failed"));
        verify(executor, never()).applyScript(any(), anyString());
        verify(auditService).recordFailure(
                eq(actor),
                eq(AuditLog.AuditAction.PROVISIONING_ROLLBACK),
                eq("Router"),
                eq(routerId.toString()),
                anyString()
        );
    }

    @Test
    @DisplayName("Rollback should fail if snapshot not found")
    void testRollbackFailsIfSnapshotNotFound() {
        // Arrange
        UUID snapshotId = UUID.randomUUID();
        String actor = "admin@test.com";

        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ApiException.class, () -> {
            provisioningService.rollback(snapshotId, actor);
        });

        verify(executor, never()).applyScript(any(), anyString());
    }

    @Test
    @DisplayName("Rollback should fail for different tenant")
    void testRollbackFailsForDifferentTenant() {
        // Arrange
        UUID snapshotId = UUID.randomUUID();
        UUID differentTenantId = UUID.randomUUID();
        String actor = "admin@test.com";

        ConfigSnapshot beforeSnapshot = new ConfigSnapshot();
        beforeSnapshot.setId(snapshotId);
        beforeSnapshot.setTenantId(differentTenantId);
        beforeSnapshot.setSnapshotType(ConfigSnapshot.SnapshotType.BEFORE);

        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(beforeSnapshot));

        // Act & Assert
        assertThrows(ApiException.class, () -> {
            provisioningService.rollback(snapshotId, actor);
        });

        verify(executor, never()).applyScript(any(), anyString());
    }

    @Test
    @DisplayName("Apply should log audit failure if configuration fails")
    void testApplyLogsAuditFailureOnError() {
        // Arrange
        String script = "/system identity set name=test-router";
        String actor = "admin@test.com";
        String description = "Test configuration";
        ProvisioningRequest request = new ProvisioningRequest(routerId, description);

        ConfigSnapshot beforeSnapshot = new ConfigSnapshot();
        beforeSnapshot.setId(UUID.randomUUID());

        when(routerRepository.findByIdAndTenantId(routerId, tenantId)).thenReturn(Optional.of(router));
        when(scriptGenerator.generateProvisioningScript(router)).thenReturn(script);
        when(snapshotService.createBeforeSnapshot(router, actor)).thenReturn(beforeSnapshot);
        doThrow(new RuntimeException("Connection failed")).when(executor).applyScript(router, script);

        // Act & Assert
        assertThrows(ApiException.class, () -> {
            provisioningService.apply(request, actor);
        });

        verify(auditService).recordFailure(
                eq(actor),
                eq(AuditLog.AuditAction.PROVISIONING_APPLY),
                eq("Router"),
                eq(routerId.toString()),
                anyString()
        );
    }
}
