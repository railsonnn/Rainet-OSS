# Rainet OSS/BSS Platform

Spring Boot 3.x, Java 17, multi-tenant OSS/BSS core for ISPs using MikroTik RouterOS.

## Stack
- Java 17
- Spring Boot 3.x (Web, Security, Data JPA)
- PostgreSQL + Flyway
- JWT (access/refresh)
- Docker / Compose

## Running locally
```bash
mvn clean package
docker-compose up --build
```

App listens on `8080`, Postgres on `5432` (db/user/pass: rainet).

## Key endpoints
- Auth: POST /auth/login, /auth/refresh, /auth/logout
- Provisioning: POST /provisioning/preview, /apply, /rollback/{id}; GET /provisioning/snapshots
- Admin: POST /admin/pops, /admin/routers; GET /admin/routers
- Customers: POST /customers; GET /customers/{id}
- Billing: POST /billing/invoices/generate; GET /billing/invoices; POST /billing/pay/{invoiceId}
- Customer portal: GET /customer/dashboard; POST /customer/unlock

## Multi-tenant
Tenant resolved from JWT `tenant_id` claim or header `X-Tenant-ID`, stored in `TenantContext` (ThreadLocal). All repositories filter by tenant in services.

## Notes
- Replace `JWT_SECRET` in envs.
- RouterOS execution is stubbed; implement `RouterOsExecutor` to call MikroTik API/SSH.
- Add rate limiting / brute-force protection as next steps.
