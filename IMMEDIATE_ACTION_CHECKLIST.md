# CHECKLIST IMEDIATO - CORREÇÕES CRÍTICAS
## Rainet-OSS - Ações Prioritárias Pré-Go-Live

**Data:** 2026-01-08  
**Status:** ❌ BLOQUEADO PARA PRODUÇÃO  
**Prazo:** 2-3 semanas

---

## 🚨 AÇÕES OBRIGATÓRIAS (BLOQUEADORES)

### 1. ✅ Schema de Banco de Dados [PRONTO]
**Status:** Migration SQL já criada  
**Arquivo:** `src/main/resources/db/migration/V2__add_missing_columns_and_tables.sql`  
**Tempo:** 5 minutos (já está pronto, só aplicar)

**Ação:**
```bash
# Opção 1: Deixar Flyway aplicar automaticamente no próximo startup
mvn spring-boot:run

# Opção 2: Aplicar manualmente
psql -h localhost -U rainet -d rainet -f src/main/resources/db/migration/V2__add_missing_columns_and_tables.sql
```

**Validação:**
```sql
-- Verificar se colunas foram adicionadas
\d customers
\d plans
\d audit_logs
```

---

### 2. JWT Secret Hardcoded [TODO]
**Tempo Estimado:** 1-2 horas  
**Risco:** 🔴 CRÍTICO - Comprometimento de autenticação

**Ação 1: Gerar segredo forte**
```bash
# Gerar segredo aleatório de 256 bits
openssl rand -base64 64

# Exemplo de output:
# RfUjXn2r5u8x/A?D(G+KbPeShVmYq3t6w9y$B&E)H@McQfTjWnZr4u7x!A%C*F-J
```

**Ação 2: Remover de application.yml**
```yaml
# ANTES (ERRADO):
security:
  jwt:
    secret: "change-me-secret-change-me-secret-change-me"

# DEPOIS (CORRETO):
security:
  jwt:
    secret: ${JWT_SECRET}
```

**Ação 3: Remover de docker-compose.yml**
```yaml
# ANTES (ERRADO):
environment:
  JWT_SECRET: change-me-secret-change-me-secret-change-me

# DEPOIS (CORRETO):
environment:
  JWT_SECRET: ${JWT_SECRET}
```

**Ação 4: Criar .env (NÃO COMMITAR)**
```bash
# Criar arquivo .env na raiz do projeto
cat > .env << 'EOF'
JWT_SECRET=<cole_o_segredo_gerado_aqui>
DB_USER=rainet
DB_PASS=rainet_secure_password_here
EOF

# Adicionar ao .gitignore
echo ".env" >> .gitignore
```

**Validação:**
```bash
# Verificar que segredo não está mais no código
grep -r "change-me-secret" . --exclude-dir=.git
# Resultado: (vazio) = sucesso
```

---

### 3. Verificação de Senha RADIUS [TODO]
**Tempo Estimado:** 30 minutos  
**Risco:** 🔴 CRÍTICO - Autenticação aceita qualquer senha

**Arquivo:** `src/main/java/com/isp/platform/provisioning/radius/RadiusServerService.java`

**Ação: Substituir método**

```java
// ANTES (linha 155-159):
private boolean verifyPassword(String plainPassword, String hashedPassword) {
    // TODO: Implement bcrypt verification
    // return BCrypt.checkpw(plainPassword, hashedPassword);
    return true; // Placeholder
}

// DEPOIS:
@Autowired
private PasswordEncoder passwordEncoder; // Adicionar no topo da classe

private boolean verifyPassword(String plainPassword, String hashedPassword) {
    if (hashedPassword == null || hashedPassword.isEmpty()) {
        log.warn("Customer has no password configured");
        return false;
    }
    return passwordEncoder.matches(plainPassword, hashedPassword);
}
```

**Validação:**
```bash
# Compilar para verificar erro de sintaxe
mvn clean compile

# Rodar testes relacionados
mvn test -Dtest=RadiusServerServiceTest
```

---

### 4. Webhook PIX Endpoint [TODO]
**Tempo Estimado:** 1 hora  
**Risco:** 🔴 CRÍTICO - Pagamentos não atualizam automaticamente

**Ação: Criar novo controller**

**Arquivo:** `src/main/java/com/isp/platform/billing/controller/WebhookController.java` (CRIAR NOVO)

```java
package com.isp.platform.billing.controller;

import com.isp.platform.billing.integration.PixGatewayService;
import com.isp.platform.billing.integration.PixPaymentRequest;
import com.isp.platform.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook endpoints for external payment gateway integrations.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PixGatewayService pixGatewayService;
    
    @Value("${pix.webhook-secret:rainet-webhook-secret-2024}")
    private String webhookSecret;

    /**
     * PIX payment webhook from Asaas/Gerencianet.
     * Called when payment status changes.
     */
    @PostMapping("/pix")
    public ResponseEntity<ApiResponse<String>> pixWebhook(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestBody PixPaymentRequest.PixWebhook webhook) {
        
        log.info("Received PIX webhook: eventId={}, status={}", 
            webhook.getEventId(), webhook.getStatus());
        
        // Validate webhook secret
        if (secret == null || !secret.equals(webhookSecret)) {
            log.warn("Invalid webhook secret received");
            return ResponseEntity.status(401)
                .body(ApiResponse.error("Unauthorized"));
        }
        
        try {
            pixGatewayService.handlePaymentWebhook(webhook);
            return ResponseEntity.ok(ApiResponse.ok("Webhook processed"));
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Processing error"));
        }
    }
}
```

**Ação: Atualizar SecurityConfig**

**Arquivo:** `src/main/java/com/isp/platform/gateway/security/SecurityConfig.java:32`

```java
// ANTES:
.requestMatchers("/actuator/health", "/auth/login", "/auth/refresh").permitAll()

// DEPOIS:
.requestMatchers("/actuator/health", "/auth/login", "/auth/refresh", "/webhooks/**").permitAll()
```

**Validação:**
```bash
# Testar com curl
curl -X POST http://localhost:8080/webhooks/pix \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Secret: rainet-webhook-secret-2024" \
  -d '{
    "eventId": "test-123",
    "status": "CONFIRMED",
    "invoiceId": "uuid-aqui"
  }'
```

---

### 5. Remover Duplicação TenantContext [TODO]
**Tempo Estimado:** 1 hora  
**Risco:** 🟠 ALTO - Inconsistência multi-tenant

**Ação 1: Deletar classe duplicada**
```bash
rm src/main/java/com/isp/platform/admin/security/TenantContext.java
```

**Ação 2: Refatorar AuditLogService**

**Arquivo:** `src/main/java/com/isp/platform/audit/service/AuditLogService.java`

```java
// ANTES (linha 28-29):
private final TenantContext tenantContext; // ❌ Injeta bean
// ...
log.setTenantId(tenantContext.getCurrentTenantId()); // ❌ Instancia

// DEPOIS:
// Remover do construtor: TenantContext tenantContext
// ...
log.setTenantId(com.isp.platform.gateway.tenant.TenantContext.getCurrentTenant()); // ✅ Static
```

**Ação 3: Deletar classes não usadas**
```bash
rm src/main/java/com/isp/platform/admin/security/UserPrincipal.java
rm src/main/java/com/isp/platform/admin/security/SystemRole.java
rm src/main/java/com/isp/platform/admin/security/TenantEnforcementFilter.java
```

**Validação:**
```bash
# Compilar para verificar não quebrou nada
mvn clean compile

# Buscar referências restantes (deve retornar vazio)
grep -r "admin.security.TenantContext" src/main/java
```

---

### 6. Corrigir Testes Falhando [TODO]
**Tempo Estimado:** 2-3 horas  
**Risco:** 🟡 MÉDIO - Funcionalidades não validadas

**Ação: Executar testes e corrigir um por um**

```bash
# Rodar apenas testes que estão falhando
mvn test -Dtest=FirewallSectionBuilderTest
mvn test -Dtest=PPPoESectionBuilderTest
mvn test -Dtest=RouterOsScriptGeneratorTest
```

**Testes a corrigir:**
1. `testAcceptFromLan` - Verificar geração de regra firewall
2. `testNatMasquerade` - Verificar geração de NAT masquerade
3. `testNoDuplicateRules` - Ajustar contador de regras esperado
4. `testGenerateCompletePPPoEConfiguration` - Verificar pool name
5. `testMultiplePPPoEPlans` - Verificar rate limits
6. `shouldConfigureRadius` - Verificar config RADIUS no script

**Dica:** Após corrigir schema (ação 1), E2E tests devem passar automaticamente.

---

## ⚡ VALIDAÇÃO COMPLETA

Após completar todas as ações acima:

```bash
# 1. Limpar e recompilar
mvn clean compile

# 2. Rodar todos os testes
mvn test

# 3. Verificar segredos não estão no código
grep -r "change-me-secret" . --exclude-dir=.git
grep -r "rainet123" src/main/java

# 4. Iniciar aplicação
mvn spring-boot:run

# 5. Testar endpoint de health
curl http://localhost:8080/actuator/health

# 6. Testar autenticação (deve falhar sem JWT válido)
curl http://localhost:8080/admin/routers
# Esperado: 401 Unauthorized
```

---

## 📋 CHECKLIST DE PROGRESSO

### Fase 1: Correções Críticas (12 horas)
- [ ] 1. Aplicar migration V2 de banco de dados (5 min)
- [ ] 2. Configurar JWT secret via variável ambiente (1-2h)
- [ ] 3. Implementar verificação senha RADIUS (30 min)
- [ ] 4. Criar WebhookController para PIX (1h)
- [ ] 5. Remover duplicação TenantContext (1h)
- [ ] 6. Corrigir 6 testes falhando (2-3h)
- [ ] 7. Rodar suite completa de testes (30 min)
- [ ] 8. Validação manual de fluxos (1h)

**Total Estimado:** 8-12 horas

---

### Fase 2: Segurança (8 horas) [PRÓXIMA ETAPA]
- [ ] Adicionar @PreAuthorize em AdminController
- [ ] Implementar rate limiting (Bucket4j)
- [ ] Validar tenant JWT vs header
- [ ] Criptografar senhas API router
- [ ] Adicionar logs de segurança

---

### Fase 3: Validação Staging (16 horas) [DEPOIS DA FASE 2]
- [ ] Deploy ambiente staging
- [ ] Testes de carga
- [ ] Provisionar router real
- [ ] Testar fluxo E2E cliente
- [ ] Validar multi-tenancy

---

## 🎯 CRITÉRIOS DE SUCESSO

Após Fase 1, o sistema deve:
- ✅ Compilar sem erros
- ✅ Todos os testes passando (45/45)
- ✅ Schema de banco completo
- ✅ JWT secret seguro (não hardcoded)
- ✅ Senha RADIUS verificada
- ✅ Webhook PIX funcional
- ✅ Zero duplicações críticas

---

## 🆘 EM CASO DE PROBLEMAS

### Erro de compilação
```bash
mvn clean
rm -rf target/
mvn compile
```

### Erro de testes
```bash
# Rodar teste específico com detalhes
mvn test -Dtest=NomeDoTest -X
```

### Erro de Flyway migration
```sql
-- Reverter migration V2 se necessário
DROP TABLE IF EXISTS plans CASCADE;
ALTER TABLE customers DROP COLUMN IF EXISTS email;
-- ... etc
```

### Aplicação não inicia
```bash
# Verificar logs
tail -f logs/spring.log

# Verificar conexão DB
psql -h localhost -U rainet -d rainet -c "SELECT version();"
```

---

## 📞 CONTATOS E SUPORTE

**Tech Lead:** [Nome]  
**DevOps:** [Nome]  
**DBA:** [Nome]

**Canal de Comunicação:** Slack #rainet-go-live

---

## 📚 DOCUMENTAÇÃO ADICIONAL

- **Auditoria Completa:** `AUDIT_REPORT_PRE_GO_LIVE.md`
- **Resumo Executivo:** `EXECUTIVE_SUMMARY_PT.md`
- **Migration SQL:** `src/main/resources/db/migration/V2__add_missing_columns_and_tables.sql`

---

**Última Atualização:** 2026-01-08  
**Versão:** 1.0  
**Status:** 🟡 EM PROGRESSO
