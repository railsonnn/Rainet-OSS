# Rollback de Configuração - Rainet OSS

## Visão Geral

O sistema de rollback permite reverter qualquer alteração aplicada a roteadores MikroTik, restaurando-os ao estado anterior. O rollback é realizado através da importação direta do snapshot BEFORE, sem utilizar diff ou merge.

## Funcionalidades

### 1. Snapshot BEFORE/AFTER

Quando uma configuração é aplicada:
- **BEFORE**: Snapshot automático da configuração atual antes da aplicação
- **AFTER**: Snapshot da nova configuração após aplicação bem-sucedida

### 2. Rollback Real

O rollback restaura o roteador ao estado exato do snapshot BEFORE:
- Importa a configuração completa do snapshot
- Não utiliza diff ou merge (aplicação direta)
- Verifica integridade através de hash SHA-256
- Cria novo snapshot AFTER após o rollback

### 3. Auditoria Completa

Todas as operações são registradas em audit_logs:
- Aplicação de configuração (sucesso/falha)
- Rollback de configuração (sucesso/falha)
- Criação de snapshots
- Actor (usuário que executou)
- Timestamp
- Detalhes da operação

## API Endpoints

### Aplicar Configuração

```http
POST /provisioning/apply
Content-Type: application/json
Authorization: Bearer <token>

{
  "routerId": "uuid-do-router",
  "description": "Descrição da mudança"
}
```

**Resposta de Sucesso:**
```json
{
  "status": "ok",
  "data": {
    "snapshotId": "uuid-do-snapshot-after"
  }
}
```

### Rollback de Configuração

```http
POST /provisioning/rollback/{snapshotId}
Authorization: Bearer <token>
```

**Parâmetros:**
- `snapshotId`: UUID do snapshot BEFORE para restaurar

**Resposta de Sucesso:**
```json
{
  "status": "ok",
  "data": "rolled back"
}
```

**Erros Possíveis:**
- `400`: Snapshot não é do tipo BEFORE
- `400`: Falha na verificação de integridade
- `404`: Snapshot não encontrado
- `403`: Snapshot pertence a outro tenant

### Listar Snapshots

```http
GET /provisioning/snapshots
Authorization: Bearer <token>
```

**Resposta:**
```json
{
  "status": "ok",
  "data": [
    {
      "id": "uuid",
      "routerId": "uuid-do-router",
      "snapshotType": "BEFORE",
      "description": "Configuration before update on router-01",
      "configHash": "abc123...",
      "appliedBy": "admin@example.com",
      "createdAt": "2026-01-07T00:00:00Z"
    }
  ]
}
```

## Fluxo de Trabalho

### Aplicação de Configuração

```
1. Cliente envia POST /provisioning/apply
2. Sistema cria snapshot BEFORE (exporta config atual)
3. Sistema aplica nova configuração
4. Sistema cria snapshot AFTER (exporta nova config)
5. Sistema registra auditoria (sucesso)
6. Retorna ID do snapshot AFTER
```

### Rollback

```
1. Cliente identifica snapshot BEFORE desejado
2. Cliente envia POST /provisioning/rollback/{snapshotId}
3. Sistema valida que é snapshot BEFORE
4. Sistema verifica integridade (hash SHA-256)
5. Sistema aplica configuração do snapshot (import direto)
6. Sistema cria snapshot AFTER do rollback
7. Sistema registra auditoria (rollback)
8. Retorna confirmação
```

## Critérios de Aceite

✅ **Router retorna ao estado anterior**
- Configuração restaurada bit-a-bit do snapshot BEFORE
- Verificação de integridade com SHA-256

✅ **Rollback executado via API**
- Endpoint REST: POST /provisioning/rollback/{snapshotId}
- Autenticação via JWT
- Multi-tenant isolado

✅ **Log registrado**
- Auditoria em audit_logs
- Status: SUCCESS/FAILURE
- Actor identificado
- Payload completo

## Modelo de Dados

### config_snapshots

```sql
id              UUID PRIMARY KEY
tenant_id       UUID NOT NULL
router_id       UUID NOT NULL
snapshot_type   VARCHAR(50) NOT NULL  -- BEFORE | AFTER
description     VARCHAR(255) NOT NULL
config_script   TEXT NOT NULL
config_hash     VARCHAR(64) NOT NULL   -- SHA-256
applied_by      VARCHAR(255) NOT NULL
created_at      TIMESTAMP NOT NULL
updated_at      TIMESTAMP NOT NULL
```

### audit_logs

```sql
id              UUID PRIMARY KEY
tenant_id       UUID NOT NULL
actor           VARCHAR(255) NOT NULL
action          VARCHAR(255) NOT NULL  -- PROVISIONING_APPLY | PROVISIONING_ROLLBACK
resource_type   VARCHAR(255) NOT NULL  -- Router
resource_id     VARCHAR(255) NOT NULL  -- Router UUID
payload         TEXT
status          VARCHAR(50) NOT NULL   -- SUCCESS | FAILURE
error_message   TEXT
created_at      TIMESTAMP NOT NULL
```

## Segurança

### Isolamento Multi-Tenant
- Snapshots filtrados por tenant_id
- Rollback bloqueia acesso cross-tenant
- Verificação em cada operação

### Integridade
- Hash SHA-256 de todas configurações
- Verificação antes de rollback
- Prevenção de corrupção de dados

### Auditoria Imutável
- Todos os eventos registrados
- Actor identificado via JWT
- Não permite modificação de logs

## Exemplos de Uso

### Cenário 1: Rollback Após Erro

```bash
# Aplicar configuração
curl -X POST http://localhost:8080/provisioning/apply \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "routerId": "abc-123",
    "description": "Enable new firewall rules"
  }'

# Resposta: {"data": {"snapshotId": "after-456"}}

# Descobriu que algo deu errado...
# Listar snapshots para encontrar BEFORE
curl -X GET http://localhost:8080/provisioning/snapshots \
  -H "Authorization: Bearer $TOKEN"

# Rollback para snapshot BEFORE
curl -X POST http://localhost:8080/provisioning/rollback/before-123 \
  -H "Authorization: Bearer $TOKEN"

# Roteador restaurado ao estado anterior!
```

### Cenário 2: Verificar Auditoria

```sql
-- Verificar histórico de configurações
SELECT 
  a.created_at,
  a.actor,
  a.action,
  a.status,
  a.payload
FROM audit_logs a
WHERE 
  a.resource_type = 'Router' 
  AND a.resource_id = 'abc-123'
ORDER BY a.created_at DESC;
```

## Limitações

- Rollback só funciona com snapshots do tipo BEFORE
- Requer conectividade com o roteador (RouterOS API)
- Não suporta rollback parcial (sempre configuração completa)
- Requer hash válido para verificação de integridade

## Próximos Passos

- [ ] Adicionar suporte a rollback em batch (múltiplos roteadores)
- [ ] Implementar agendamento de rollback
- [ ] Adicionar notificações (email/webhook) após rollback
- [ ] Dashboard visual do histórico de snapshots
- [ ] Comparação visual entre snapshots (diff view)
