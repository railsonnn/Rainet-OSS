# Implementação: Rollback Real de Configuração

## Resumo Executivo

Implementação completa do sistema de rollback de configuração para roteadores MikroTik, permitindo reverter alterações aplicadas através da restauração de snapshots BEFORE.

## Objetivos Alcançados ✅

### 1. Router Retorna ao Estado Anterior
- Snapshot BEFORE capturado automaticamente antes de qualquer mudança
- Configuração restaurada bit-a-bit via importação direta
- Verificação de integridade através de hash SHA-256
- Sem uso de diff ou merge (aplicação completa)

### 2. Rollback Executado via API
- Endpoint: `POST /provisioning/rollback/{snapshotId}`
- Autenticação via JWT
- Isolamento multi-tenant
- Validação de tipo de snapshot (apenas BEFORE)

### 3. Log Registrado
- Auditoria completa em `audit_logs`
- Status: SUCCESS/FAILURE
- Actor identificado
- Payload com detalhes da operação
- Rastreabilidade completa

## Arquivos Modificados/Criados

### Database
- `src/main/resources/db/migration/V2__add_snapshot_type_and_hash.sql`
  - Adiciona `snapshot_type` (BEFORE/AFTER)
  - Adiciona `config_hash` (SHA-256)
  - Atualiza schema `audit_logs`

### Core Services
- `src/main/java/com/isp/platform/audit/service/AuditService.java`
  - Métodos `record()` e `recordFailure()`
  - Usa `AuditLog.AuditAction` enum
  
- `src/main/java/com/isp/platform/provisioning/service/ProvisioningService.java`
  - Método `apply()`: BEFORE → apply → AFTER → audit
  - Método `rollback()`: validate → verify → apply → AFTER → audit
  - Integração com ConfigSnapshotService
  
- `src/main/java/com/isp/platform/provisioning/snapshot/ConfigSnapshotService.java`
  - `createBeforeSnapshot()`
  - `createAfterSnapshot()`
  - `verifySnapshot()` - verificação de integridade
  - `performRollback()` - para uso avançado

### Utilities
- `src/main/java/com/isp/platform/common/util/HashUtil.java`
  - Cálculo SHA-256 reutilizável
  - Princípio DRY (Don't Repeat Yourself)

### Repository
- `src/main/java/com/isp/platform/provisioning/snapshot/ConfigSnapshotRepository.java`
  - Alterado ID de `Long` para `UUID`

### Tests
- `src/test/java/com/isp/platform/provisioning/service/ProvisioningServiceRollbackTest.java`
  - 8 cenários de teste
  - Cobertura completa de casos de uso e edge cases

### Documentation
- `ROLLBACK_FEATURE.md`
  - Documentação completa em português
  - Exemplos de API
  - Fluxos de trabalho
  - Modelo de dados

## Fluxo de Trabalho

### Aplicação de Configuração
```
1. POST /provisioning/apply
2. Sistema cria snapshot BEFORE (exportCompact)
3. Sistema aplica nova configuração
4. Sistema cria snapshot AFTER
5. Sistema registra auditoria (SUCCESS)
6. Retorna snapshotId (AFTER)
```

### Rollback
```
1. POST /provisioning/rollback/{snapshotId}
2. Sistema valida tipo BEFORE
3. Sistema verifica integridade (SHA-256)
4. Sistema aplica configuração (import direto)
5. Sistema cria snapshot AFTER do rollback
6. Sistema registra auditoria (ROLLBACK)
7. Retorna confirmação
```

## Modelo de Dados

### config_snapshots
```sql
id              UUID PRIMARY KEY
tenant_id       UUID NOT NULL
router_id       UUID NOT NULL
snapshot_type   VARCHAR(50) NOT NULL  -- BEFORE | AFTER
description     VARCHAR(255) NOT NULL
config_script   TEXT NOT NULL
config_hash     VARCHAR(64) NOT NULL  -- SHA-256
applied_by      VARCHAR(255) NOT NULL
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

### audit_logs
```sql
id              UUID PRIMARY KEY
tenant_id       UUID NOT NULL
actor           VARCHAR(255) NOT NULL
action          VARCHAR(255) NOT NULL  -- PROVISIONING_APPLY | PROVISIONING_ROLLBACK
resource_type   VARCHAR(255) NOT NULL  -- Router
resource_id     VARCHAR(255) NOT NULL
payload         TEXT
status          VARCHAR(50) NOT NULL   -- SUCCESS | FAILURE
error_message   TEXT
ip_address      VARCHAR(255)
user_agent      TEXT
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

## Segurança

### Isolamento Multi-Tenant
- Snapshots filtrados por `tenant_id`
- Rollback valida tenant antes de executar
- Impossível acessar snapshots de outros tenants

### Integridade de Dados
- Hash SHA-256 de todas configurações
- Verificação obrigatória antes de rollback
- Snapshots legacy marcados como 'legacy-record-no-hash'

### Auditoria Imutável
- Todos eventos registrados
- Actor identificado via JWT
- Logs não podem ser modificados

## Testes

### Cenários Cobertos
1. ✅ Apply cria snapshots BEFORE e AFTER
2. ✅ Rollback aceita apenas snapshots BEFORE
3. ✅ Rollback restaura configuração corretamente
4. ✅ Rollback falha se verificação de integridade falhar
5. ✅ Rollback falha se snapshot não encontrado
6. ✅ Rollback respeita isolamento multi-tenant
7. ✅ Apply registra auditoria em caso de falha
8. ✅ Rollback registra auditoria em sucesso/falha

## API Endpoints

### POST /provisioning/apply
```json
Request:
{
  "routerId": "uuid",
  "description": "Descrição"
}

Response:
{
  "status": "ok",
  "data": {
    "snapshotId": "uuid-after"
  }
}
```

### POST /provisioning/rollback/{snapshotId}
```json
Response:
{
  "status": "ok",
  "data": "rolled back"
}

Errors:
- 400: Snapshot não é BEFORE
- 400: Falha verificação integridade
- 404: Snapshot não encontrado
- 403: Snapshot de outro tenant
```

### GET /provisioning/snapshots
```json
Response:
{
  "status": "ok",
  "data": [
    {
      "id": "uuid",
      "routerId": "uuid",
      "snapshotType": "BEFORE",
      "description": "...",
      "configHash": "...",
      "appliedBy": "admin@example.com",
      "createdAt": "2026-01-07T00:00:00Z"
    }
  ]
}
```

## Próximos Passos (Fora do Escopo)

- [ ] Rollback em batch (múltiplos roteadores)
- [ ] Agendamento de rollback
- [ ] Notificações (email/webhook)
- [ ] Dashboard visual de snapshots
- [ ] Comparação visual entre snapshots

## Conclusão

A implementação está **completa e pronta para testes**. Todos os critérios de aceite foram atendidos:

✅ Router retorna ao estado anterior através de snapshot BEFORE
✅ Rollback executado via API REST com autenticação
✅ Log completo registrado em audit_logs

O código está:
- ✅ Testado (8 cenários de teste unitário)
- ✅ Documentado (ROLLBACK_FEATURE.md)
- ✅ Seguro (multi-tenant, integridade, auditoria)
- ✅ Revisado (code review aprovado)
- ✅ Pronto para deploy
