# Rainet OSS/BSS – README do Projeto

## Visão Geral

Rainet OSS/BSS é uma plataforma para gestão de ISPs com automação completa de roteadores MikroTik (RouterOS), autenticação PPPoE via RADIUS, cobrança via PIX e trilhas de auditoria imutáveis, com segurança RBAC e multi‑tenant.

Este README resume a proposta original do projeto e explicita, de forma objetiva, o status de cada tarefa com base no código atualmente implementado.

---

## Proposta Original e Status das Tarefas

- Automação RouterOS (conexão, execução de scripts, exportação compacta)
  - Status: Concluído
  - Detalhes: `RouterOsExecutor` e `RouterOsApiExecutor` implementados para testar conexão, aplicar scripts via `/import` e exportar `/export compact`.

- Geração de scripts RouterOS idempotentes e modulares
  - Status: Concluído
  - Detalhes: `RouterOsScriptBuilder` + 8 builders (Interfaces, Bridge, WAN, LAN/DHCP, PPPoE, Firewall/NAT, QoS, Serviços) geram script completo e re‑aplicável.

- Snapshots BEFORE/AFTER + Rollback
  - Status: Concluído
  - Detalhes: `ConfigSnapshotService` cria snapshots com hash SHA‑256, consulta último BEFORE e executa rollback aplicando script anterior.

- PPPoE + RADIUS (autenticação e rate‑limit Mikrotik)
  - Status: Concluído funcionalmente / Parcial em segurança
  - Detalhes: `RadiusServerService` retorna atributos `Mikrotik-Rate-Limit` com limites por plano; verificação de senha está placeholder (recomenda-se bcrypt/integração com FreeRADIUS).

- Cobrança PIX (Asaas/Gerencianet) e desbloqueio automático
  - Status: Parcial
  - Detalhes: Asaas implementado em `PixGatewayService` (QR Code + webhook → desbloqueio). Integração Gerencianet está como TODO/mocked.

- Segurança (RBAC) e Multi‑Tenancy
  - Status: Concluído
  - Detalhes: `SystemRole`, `UserPrincipal`, `TenantContext` e `TenantEnforcementFilter` garantem isolamento por tenant e permissões por perfil.

- Auditoria imutável de operações críticas
  - Status: Concluído
  - Detalhes: `AuditLog` com enums `AuditAction/AuditStatus`, índices e `AuditLogService` para registro de provisionamento, rollback, billing, auth etc.

- Testes E2E do piloto
  - Status: Parcial
  - Detalhes: `IsoPilotE2ETest` contém a suíte e cenários detalhados; implementação dos testes e ambiente real ainda pendentes.

- Controladores REST (API pública)
  - Status: Pendente
  - Detalhes: Serviços estão prontos para exposição; endpoints não foram implementados.

- Migrações de banco (Flyway)
  - Status: Pendente
  - Detalhes: Flyway configurado; scripts SQL de criação/índices ainda não escritos.

- Frontend administrativo
  - Status: Fora de escopo atual / Pendente

Resumo: Núcleo de automação, segurança, auditoria e integrações principais está concluído; PIX (Gerencianet), REST e migrações permanecem em aberto. Reforço de segurança de senhas no RADIUS é recomendável.

---

## Principais Módulos e Arquivos

- Provisionamento MikroTik
  - `src/main/java/.../provisioning/mikrotik/RouterOsExecutor.java`
  - `src/main/java/.../provisioning/mikrotik/RouterOsApiExecutor.java`
  - `src/main/java/.../provisioning/mikrotik/builder/*`

- Snapshots e Rollback
  - `src/main/java/.../provisioning/snapshot/ConfigSnapshot.java`
  - `src/main/java/.../provisioning/snapshot/ConfigSnapshotService.java`
  - `src/main/java/.../provisioning/snapshot/ConfigSnapshotRepository.java`

- RADIUS / PPPoE
  - `src/main/java/.../provisioning/radius/RadiusServerService.java`
  - `src/main/java/.../provisioning/radius/RadiusAuthRequest.java`

- Cobrança PIX
  - `src/main/java/.../billing/integration/PixGatewayService.java`
  - `src/main/java/.../billing/integration/PixPaymentRequest.java`

- Segurança e Multi‑Tenant
  - `src/main/java/.../admin/security/SystemRole.java`
  - `src/main/java/.../admin/security/UserPrincipal.java`
  - `src/main/java/.../admin/security/TenantContext.java`
  - `src/main/java/.../admin/security/TenantEnforcementFilter.java`

- Auditoria
  - `src/main/java/.../audit/domain/AuditLog.java`
  - `src/main/java/.../audit/domain/AuditLogRepository.java`
  - `src/main/java/.../audit/service/AuditLogService.java`

- Testes
  - `src/test/java/.../test/IsoPilotE2ETest.java` (cenários definidos; implementação pendente)

Documentação complementar:
- `README_IMPLEMENTATION.md` – visão detalhada técnica
- `SETUP_AND_DEPLOYMENT_GUIDE.md` – guia de setup e deploy
- `application.yml.example` – template de configuração

---

## Como Executar (dev)

1) Configurar `application.yml` a partir de `application.yml.example`.
2) Garantir acesso ao banco PostgreSQL e ao roteador MikroTik.
3) Build e execução:

```bash
mvn clean install
mvn spring-boot:run
```

### Executar com Docker Compose

```bash
# Build e iniciar serviços (PostgreSQL + aplicação)
docker-compose up --build

# A aplicação estará disponível em http://localhost:8080/api
# Credenciais padrão: username=admin, password=change-me
```

### Testar Endpoints com HTTP Basic Auth

Todos os endpoints agora usam autenticação HTTP Basic (DB-backed) e estão sob o path `/api`:

```bash
# Testar endpoint de preview de provisionamento
curl -u admin:change-me http://localhost:8080/api/provisioning/preview \
  -H "Content-Type: application/json" \
  -d '{
    "routerId": "UUID-do-router",
    "description": "Teste de configuração"
  }'

# Aplicar configuração (retorna snapshotId)
curl -u admin:change-me -X POST http://localhost:8080/api/provisioning/apply \
  -H "Content-Type: application/json" \
  -d '{
    "routerId": "UUID-do-router",
    "description": "Aplicar configuração"
  }'

# Fazer rollback de um snapshot
curl -u admin:change-me -X POST http://localhost:8080/api/provisioning/rollback/{snapshotId}

# Listar snapshots
curl -u admin:change-me http://localhost:8080/api/provisioning/snapshots
```

**Nota:** Suporte a JWT foi removido. A aplicação agora usa autenticação HTTP Basic com backend em banco de dados PostgreSQL.

---

## Próximos Passos Recomendados

- Implementar controladores REST (Provisioning, Billing/PIX, Customers, Audit).
- Escrever migrações Flyway (DDL + índices) e dados de bootstrap.
- Completar integração Gerencianet no `PixGatewayService` e tornar webhook idempotente.
- Fortalecer autenticação no RADIUS (bcrypt/FreeRADIUS SQL) e testes em lab real.
- Implementar e executar a suíte E2E contra ambiente de teste (MikroTik + FreeRADIUS + Asaas).
- Adicionar CI/CD e contêiner (Docker/K8s) conforme `SETUP_AND_DEPLOYMENT_GUIDE.md`.

---

## Licença

Projeto interno. Não adicionar cabeçalhos de licença a menos que solicitado.
# Rainet-OSS

Estado e próximos passos do projeto Rainet-OSS.

## Status (atualizado em 2026-01-12)

- RouterOS API (execução real): ✅ implementado — `RouterOsApiExecutor` conecta via API RouterOS (porta 8728) e executa scripts reais.
- Gerador modular de scripts (.rsc): ✅ implementado — `RouterOsScriptBuilder` com 8 builders modulares gera scripts idempotentes.
- Snapshots BEFORE/AFTER e rollback: ✅ implementado — `ProvisioningController` e `ProvisioningService` com endpoints /preview, /apply, /rollback.
- Segurança RBAC: ✅ implementado — HTTP Basic Auth DB-backed substituiu JWT; roles mapeadas para Spring Security GrantedAuthority.
- Multi-tenant enforcement: ✅ implementado — TenantContext e enforcement por tenant nos serviços.
- PPPoE + FreeRADIUS: ⚠️ parcial — estrutura presente, integração real pendente.
- Billing via PIX: ⚠️ parcial — Asaas implementado, Gerencianet pendente.
- Auditoria de ações críticas: ✅ implementado — `AuditLog` com enums e serviços de auditoria.
- Testes de campo / E2E: ❌ pendente — suíte definida, implementação pendente.
- Infra para produção: ⚠️ parcial — Docker/Docker Compose configurados; HTTPS, CI/CD pendentes.

**Mudanças recentes:**
- JWT removido, substituído por HTTP Basic Auth com backend em banco de dados
- Base path configurado como `/api` para todos os endpoints
- RouterOS API com escaping seguro de nomes de arquivos
- DataInitializer cria tenant e usuário admin automaticamente na primeira inicialização
- Endpoints de provisioning corrigidos e validados

## Próximos passos sugeridos

1) Implementar as funcionalidades críticas (RouterOS, geração de scripts, snapshots/rollback, PPPoE/RADIUS, PIX, auditoria) em PRs separados.
2) Completar RBAC e multi-tenant com filtros/enforcement e testes de integração.
3) Adicionar testes E2E mínimos cobrindo provisão, autenticação PPPoE e rollback.
4) Evoluir a base de infra: CI/CD (GH Actions), HTTPS, gestão de segredos e exemplos de configuração de produção.

## Referências

- README_IMPLEMENTATION.md: visão alvo completa da plataforma.
- application.yml.example: modelo de configuração.
- Documentação de arquitetura e guias de deploy (quando disponíveis) devem ser alinhados ao status acima.
