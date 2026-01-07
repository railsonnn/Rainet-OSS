# Snapshot BEFORE/AFTER Implementation Summary

## Overview
This implementation adds complete BEFORE/AFTER snapshot functionality for RouterOS configurations with SHA-256 integrity verification, as requested in the issue.

## Changes Made

### 1. Database Migration (V2__add_snapshot_type_and_hash.sql)
Created a new Flyway migration to add missing columns to the `config_snapshots` table:
- `snapshot_type` (VARCHAR(10)): Stores whether snapshot is BEFORE or AFTER
- `config_hash` (VARCHAR(64)): Stores SHA-256 hash for integrity verification
- Added indexes for performance optimization

### 2. ConfigSnapshot Entity
The entity already existed with the proper structure including:
- `SnapshotType` enum with BEFORE and AFTER values
- All necessary fields (router, tenantId, configScript, configHash, appliedBy)
- Proper relationships with Router and Tenant

### 3. ConfigSnapshotService
Enhanced the existing service:
- ✅ `createBeforeSnapshot()`: Captures configuration BEFORE changes
- ✅ `createAfterSnapshot()`: Captures configuration AFTER changes
- ✅ `exportCompact()`: Uses RouterOS `/export compact` command
- ✅ SHA-256 hashing for integrity verification
- ✅ `verifySnapshot()`: Validates snapshot integrity
- ✅ `performRollback()`: Rollback to previous BEFORE snapshot
- Updated to use UUID instead of Long for consistency

### 4. ProvisioningService Integration
Updated the provisioning workflow to automatically create snapshots:
```java
// In apply() method:
1. Create BEFORE snapshot
2. Apply configuration changes
3. Create AFTER snapshot
4. Return AFTER snapshot ID
```

The rollback functionality now delegates to ConfigSnapshotService's `performRollback()` method.

### 5. Comprehensive Unit Tests (ConfigSnapshotServiceTest)
Created 10 test cases covering:
- ✅ BEFORE snapshot creation
- ✅ AFTER snapshot creation
- ✅ Hash consistency verification
- ✅ Snapshot integrity validation
- ✅ Rollback functionality
- ✅ Error handling (invalid snapshot types, missing snapshots)
- ✅ Latest snapshot retrieval

### 6. Minor Fixes
- Updated Mockito version from 5.2.1 to 5.3.1
- Fixed UUID type consistency across repository and service
- Added missing UUID import in ConfigSnapshotService

## Acceptance Criteria Met

✅ **Snapshot saved before and after**: The ProvisioningService now creates both BEFORE and AFTER snapshots automatically

✅ **Content integrity**: SHA-256 hash is calculated and stored for every snapshot, with verification method available

✅ **Associated with router and tenant**: All snapshots include router_id and tenant_id foreign keys

✅ **Execute `/export compact`**: The RouterOsExecutor.exportCompact() method executes this command

## How It Works

### Creating Snapshots
When a configuration is applied to a router:

1. **BEFORE Snapshot**: 
   - Executes `/export compact` on the router
   - Calculates SHA-256 hash of the configuration
   - Saves to database with type=BEFORE

2. **Apply Configuration**:
   - Generates and applies the new configuration script

3. **AFTER Snapshot**:
   - Executes `/export compact` again
   - Calculates SHA-256 hash
   - Saves to database with type=AFTER

### Rollback Process
To rollback to a previous configuration:
1. Retrieve the BEFORE snapshot by ID
2. Apply the snapshot's configuration script to the router
3. Create a new AFTER snapshot documenting the rollback
4. Preserve the original hash for audit trail

### Integrity Verification
At any time, you can verify a snapshot's integrity:
```java
boolean isValid = snapshotService.verifySnapshot(snapshot);
```
This recalculates the SHA-256 hash and compares it with the stored hash.

## Database Schema

```sql
CREATE TABLE config_snapshots (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL,
    router_id UUID NOT NULL,
    snapshot_type VARCHAR(10) NOT NULL,  -- NEW: 'BEFORE' or 'AFTER'
    description VARCHAR(255) NOT NULL,
    config_script TEXT NOT NULL,
    config_hash VARCHAR(64) NOT NULL,    -- NEW: SHA-256 hash
    applied_by VARCHAR(255) NOT NULL,
    CONSTRAINT fk_snapshot_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_snapshot_router FOREIGN KEY (router_id) REFERENCES routers(id)
);

-- Indexes for performance
CREATE INDEX idx_config_snapshots_type ON config_snapshots(snapshot_type);
CREATE INDEX idx_config_snapshots_router_type ON config_snapshots(router_id, snapshot_type);
```

## Example Usage

```java
// Apply a configuration with automatic BEFORE/AFTER snapshots
UUID snapshotId = provisioningService.apply(request, "admin");

// Later, rollback if needed
provisioningService.rollback(snapshotId, "admin");

// Verify integrity
ConfigSnapshot snapshot = snapshotRepository.findById(snapshotId).get();
boolean isValid = snapshotService.verifySnapshot(snapshot);

// Get latest BEFORE snapshot for a router
Optional<ConfigSnapshot> latest = snapshotService.getLatestBeforeSnapshot(router);
```

## Testing

The implementation includes comprehensive unit tests that verify:
- Snapshot creation and persistence
- Hash calculation and consistency
- Rollback functionality
- Error handling
- Repository interactions

To run the tests:
```bash
mvn test -Dtest=ConfigSnapshotServiceTest
```

## Security Considerations

1. **Integrity**: SHA-256 hashing ensures configuration hasn't been tampered with
2. **Multi-tenancy**: All snapshots are scoped to tenant_id
3. **Audit Trail**: Every snapshot records who applied it (appliedBy field)
4. **Immutability**: Snapshots are never modified, only created
5. **Access Control**: Tenant context is enforced at service layer

## Performance Notes

- Database indexes on snapshot_type and (router_id, snapshot_type) for fast queries
- Lazy loading of Router relationship to avoid unnecessary joins
- SHA-256 calculation is fast (~microseconds for typical configs)
- Snapshot storage is TEXT type, suitable for large configurations

## Future Enhancements (Optional)

- Add snapshot compression for large configurations
- Implement snapshot retention policies (auto-delete old snapshots)
- Add diff comparison between BEFORE/AFTER snapshots
- Create REST API endpoints for snapshot management
- Add snapshot export/download functionality
