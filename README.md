# Rainet OSS/BSS Platform

Spring Boot 3.x, Java 17, multi-tenant OSS/BSS core for ISPs using MikroTik RouterOS.

## Stack
- Java 17
- Spring Boot 3.x (Web, Security, Data JPA)
- PostgreSQL + Flyway
- FreeRADIUS (PPPoE authentication)
- JWT (access/refresh)
- Docker / Compose

## Features
✅ **PPPoE + FreeRADIUS Integration**
- Customer authentication via RADIUS
- Automatic bandwidth control (Mikrotik-Rate-Limit)
- Session accounting and statistics
- Block/unblock customers
- REST API for user management

✅ **Multi-tenant Architecture**
- Complete tenant isolation
- Per-tenant plans and customers

✅ **MikroTik Integration**
- RouterOS configuration generation
- PPPoE server automation

## Running locally
```bash
mvn clean package
docker-compose up --build
```

App listens on `8080`, Postgres on `5432` (db/user/pass: rainet), FreeRADIUS on `1812/1813` UDP.

## Key endpoints

### Authentication
- POST /auth/login, /auth/refresh, /auth/logout

### RADIUS Management (NEW!)
- POST /api/v1/radius/users - Create PPPoE user
- DELETE /api/v1/radius/users/{username} - Remove user
- POST /api/v1/radius/users/{customerId}/block - Block customer
- POST /api/v1/radius/users/{customerId}/unblock - Unblock customer
- GET /api/v1/radius/sessions - Active sessions
- GET /api/v1/radius/sessions/user/{username}/stats - Usage statistics

### Provisioning
- POST /provisioning/preview, /apply, /rollback/{id}
- GET /provisioning/snapshots

### Admin
- POST /admin/pops, /admin/routers
- GET /admin/routers

### Customers
- POST /customers
- GET /customers/{id}

### Billing
- POST /billing/invoices/generate
- GET /billing/invoices
- POST /billing/pay/{invoiceId}

### Customer Portal
- GET /customer/dashboard
- POST /customer/unlock

## Multi-tenant
Tenant resolved from JWT `tenant_id` claim or header `X-Tenant-ID`, stored in `TenantContext` (ThreadLocal). All repositories filter by tenant in services.

## Documentation
- [PPPoE + FreeRADIUS Integration Guide](PPPOE_FREERADIUS_INTEGRATION.md)
- [Testing Guide](TESTE_PPPOE_RADIUS.md)
- [Security Analysis](SECURITY_SUMMARY_RADIUS.md)
- [Implementation Summary](IMPLEMENTATION_SUMMARY.md)

## Notes
- Replace `JWT_SECRET` and `RADIUS_SECRET` in envs
- FreeRADIUS uses PostgreSQL backend (radcheck, radreply, radacct tables)
- PPPoE authentication ready for production with MikroTik routers
