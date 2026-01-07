package com.isp.platform.provisioning.snapshot;

import com.isp.platform.provisioning.domain.Pop;
import com.isp.platform.provisioning.domain.Router;
import com.isp.platform.provisioning.mikrotik.RouterOsExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConfigSnapshotService.
 * Tests BEFORE/AFTER snapshot creation, hashing, and rollback.
 */
@ExtendWith(MockitoExtension.class)
class ConfigSnapshotServiceTest {

    @Mock
    private ConfigSnapshotRepository snapshotRepository;

    @Mock
    private RouterOsExecutor routerOsExecutor;

    @InjectMocks
    private ConfigSnapshotService snapshotService;

    private Router testRouter;
    private String testConfig;
    private String testUser;

    @BeforeEach
    void setUp() {
        testUser = "test-admin";
        testConfig = """
                # RouterOS script configuration
                /system identity set name="ISP-Router-1"
                /interface bridge add name=bridge1
                """;

        // Create test router
        testRouter = new Router();
        testRouter.setId(UUID.randomUUID());
        testRouter.setTenantId(UUID.randomUUID());
        testRouter.setHostname("ISP-Router-1");
        testRouter.setManagementAddress("192.168.1.1");
        testRouter.setRouterOsVersion("7.9");
        testRouter.setApiUsername("admin");
        testRouter.setApiPassword("password");
        
        Pop testPop = new Pop();
        testPop.setId(UUID.randomUUID());
        testPop.setTenantId(testRouter.getTenantId());
        testPop.setName("Main POP");
        testPop.setCity("São Paulo");
        testRouter.setPop(testPop);
    }

    @Test
    void testCreateBeforeSnapshot() {
        // Arrange
        when(routerOsExecutor.exportCompact(testRouter)).thenReturn(testConfig);
        when(snapshotRepository.save(any(ConfigSnapshot.class))).thenAnswer(invocation -> {
            ConfigSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(UUID.randomUUID());
            return snapshot;
        });

        // Act
        ConfigSnapshot snapshot = snapshotService.createBeforeSnapshot(testRouter, testUser);

        // Assert
        assertNotNull(snapshot);
        assertEquals(ConfigSnapshot.SnapshotType.BEFORE, snapshot.getSnapshotType());
        assertEquals(testRouter, snapshot.getRouter());
        assertEquals(testRouter.getTenantId(), snapshot.getTenantId());
        assertEquals(testUser, snapshot.getAppliedBy());
        assertEquals(testConfig, snapshot.getConfigScript());
        assertNotNull(snapshot.getConfigHash());
        assertEquals(64, snapshot.getConfigHash().length()); // SHA-256 produces 64 hex chars
        
        verify(routerOsExecutor, times(1)).exportCompact(testRouter);
        verify(snapshotRepository, times(1)).save(any(ConfigSnapshot.class));
    }

    @Test
    void testCreateAfterSnapshot() {
        // Arrange
        when(routerOsExecutor.exportCompact(testRouter)).thenReturn(testConfig);
        when(snapshotRepository.save(any(ConfigSnapshot.class))).thenAnswer(invocation -> {
            ConfigSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(UUID.randomUUID());
            return snapshot;
        });

        // Act
        ConfigSnapshot snapshot = snapshotService.createAfterSnapshot(testRouter, testUser);

        // Assert
        assertNotNull(snapshot);
        assertEquals(ConfigSnapshot.SnapshotType.AFTER, snapshot.getSnapshotType());
        assertEquals(testRouter, snapshot.getRouter());
        assertEquals(testRouter.getTenantId(), snapshot.getTenantId());
        assertEquals(testUser, snapshot.getAppliedBy());
        assertEquals(testConfig, snapshot.getConfigScript());
        assertNotNull(snapshot.getConfigHash());
        assertEquals(64, snapshot.getConfigHash().length());
        
        verify(routerOsExecutor, times(1)).exportCompact(testRouter);
        verify(snapshotRepository, times(1)).save(any(ConfigSnapshot.class));
    }

    @Test
    void testSnapshotHashConsistency() {
        // Arrange
        when(routerOsExecutor.exportCompact(testRouter)).thenReturn(testConfig);
        when(snapshotRepository.save(any(ConfigSnapshot.class))).thenAnswer(invocation -> {
            ConfigSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(UUID.randomUUID());
            return snapshot;
        });

        // Act
        ConfigSnapshot snapshot1 = snapshotService.createBeforeSnapshot(testRouter, testUser);
        ConfigSnapshot snapshot2 = snapshotService.createBeforeSnapshot(testRouter, testUser);

        // Assert - Same content should produce same hash
        assertEquals(snapshot1.getConfigHash(), snapshot2.getConfigHash());
    }

    @Test
    void testVerifySnapshot() {
        // Arrange - Mock the export to return the test config
        when(routerOsExecutor.exportCompact(testRouter)).thenReturn(testConfig);
        when(snapshotRepository.save(any(ConfigSnapshot.class))).thenAnswer(invocation -> {
            ConfigSnapshot s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        // Act - Create a snapshot which will have the correct hash
        ConfigSnapshot created = snapshotService.createBeforeSnapshot(testRouter, testUser);
        boolean isValid = snapshotService.verifySnapshot(created);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void testPerformRollback() {
        // Arrange
        UUID snapshotId = UUID.randomUUID();
        ConfigSnapshot beforeSnapshot = new ConfigSnapshot();
        beforeSnapshot.setId(snapshotId);
        beforeSnapshot.setRouter(testRouter);
        beforeSnapshot.setTenantId(testRouter.getTenantId());
        beforeSnapshot.setSnapshotType(ConfigSnapshot.SnapshotType.BEFORE);
        beforeSnapshot.setConfigScript(testConfig);
        beforeSnapshot.setConfigHash("abc123");
        beforeSnapshot.setAppliedBy("original-user");
        beforeSnapshot.setDescription("Before snapshot");

        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(beforeSnapshot));
        doNothing().when(routerOsExecutor).applyScript(eq(testRouter), eq(testConfig));
        when(snapshotRepository.save(any(ConfigSnapshot.class))).thenAnswer(invocation -> {
            ConfigSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(UUID.randomUUID());
            return snapshot;
        });

        // Act
        ConfigSnapshot afterSnapshot = snapshotService.performRollback(snapshotId, testUser);

        // Assert
        assertNotNull(afterSnapshot);
        assertEquals(ConfigSnapshot.SnapshotType.AFTER, afterSnapshot.getSnapshotType());
        assertEquals(testRouter, afterSnapshot.getRouter());
        assertEquals(testConfig, afterSnapshot.getConfigScript());
        assertEquals(beforeSnapshot.getConfigHash(), afterSnapshot.getConfigHash());
        assertEquals(testUser, afterSnapshot.getAppliedBy());
        assertTrue(afterSnapshot.getDescription().contains("Rollback"));
        
        verify(routerOsExecutor, times(1)).applyScript(testRouter, testConfig);
        verify(snapshotRepository, times(1)).save(any(ConfigSnapshot.class));
    }

    @Test
    void testRollbackToAfterSnapshotThrowsException() {
        // Arrange
        UUID snapshotId = UUID.randomUUID();
        ConfigSnapshot afterSnapshot = new ConfigSnapshot();
        afterSnapshot.setId(snapshotId);
        afterSnapshot.setSnapshotType(ConfigSnapshot.SnapshotType.AFTER);

        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(afterSnapshot));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            snapshotService.performRollback(snapshotId, testUser)
        );
        
        assertTrue(exception.getMessage().contains("Can only rollback to BEFORE snapshots"));
        verify(routerOsExecutor, never()).applyScript(any(), any());
    }

    @Test
    void testRollbackWithInvalidIdThrowsException() {
        // Arrange
        UUID invalidId = UUID.randomUUID();
        when(snapshotRepository.findById(invalidId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            snapshotService.performRollback(invalidId, testUser)
        );
        
        assertTrue(exception.getMessage().contains("Snapshot not found"));
        verify(routerOsExecutor, never()).applyScript(any(), any());
    }

    @Test
    void testGetLatestBeforeSnapshot() {
        // Arrange
        ConfigSnapshot latestSnapshot = new ConfigSnapshot();
        latestSnapshot.setId(UUID.randomUUID());
        latestSnapshot.setSnapshotType(ConfigSnapshot.SnapshotType.BEFORE);
        latestSnapshot.setRouter(testRouter);

        when(snapshotRepository.findTopByRouterAndSnapshotTypeOrderByCreatedAtDesc(
            testRouter, ConfigSnapshot.SnapshotType.BEFORE
        )).thenReturn(Optional.of(latestSnapshot));

        // Act
        Optional<ConfigSnapshot> result = snapshotService.getLatestBeforeSnapshot(testRouter);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(latestSnapshot, result.get());
        verify(snapshotRepository, times(1)).findTopByRouterAndSnapshotTypeOrderByCreatedAtDesc(
            testRouter, ConfigSnapshot.SnapshotType.BEFORE
        );
    }
}
