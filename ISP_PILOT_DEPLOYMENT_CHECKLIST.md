# Checklist de Implantação do ISP Piloto

## Objetivo
Validar o sistema Rainet OSS/BSS em operação real com ISP piloto, garantindo 7 dias de operação contínua sem intervenção manual.

---

## 📋 Pré-Requisitos

### Infraestrutura
- [ ] Servidor Linux (Ubuntu 22.04 LTS recomendado) com:
  - [ ] 4 vCPUs mínimo
  - [ ] 8 GB RAM mínimo
  - [ ] 50 GB disco SSD
  - [ ] IP público fixo
  - [ ] Portas abertas: 80, 443, 8080, 5432, 1812/UDP (RADIUS)
- [ ] MikroTik RouterOS (versão 7.x ou superior) com:
  - [ ] RouterOS licença válida
  - [ ] API habilitada (porta 8728 ou 8729 SSL)
  - [ ] Usuário API com permissões completas
  - [ ] Interface WAN configurada com IP público
  - [ ] Interface LAN/Bridge criada
- [ ] Banco de dados PostgreSQL 15+
- [ ] Domínio DNS configurado (ex: isp.rainet.com.br)
- [ ] Certificado SSL/TLS (Let's Encrypt recomendado)

### Integrações Externas
- [ ] Conta ativa no gateway PIX (Asaas ou Gerencianet)
- [ ] API key do gateway de pagamento
- [ ] Webhook URL registrada no gateway

---

## 🔧 Instalação e Configuração

### 1. Preparação do Ambiente
- [ ] Clone o repositório: `git clone https://github.com/railsonnn/Rainet-OSS.git`
- [ ] Instale Java 17: `sudo apt install openjdk-17-jdk`
- [ ] Instale Maven: `sudo apt install maven`
- [ ] Instale Docker e Docker Compose: `curl -fsSL https://get.docker.com | sh`
- [ ] Instale PostgreSQL: `docker-compose up -d postgres`

### 2. Configuração do Banco de Dados
- [ ] Criar banco de dados: `createdb rainet_oss`
- [ ] Configurar `application.yml`:
  ```yaml
  spring:
    datasource:
      url: jdbc:postgresql://localhost:5432/rainet_oss
      username: postgres
      password: <SENHA_SEGURA>
  ```
- [ ] Executar migrations: `mvn flyway:migrate`
- [ ] Verificar tabelas criadas: `psql rainet_oss -c "\dt"`
- [ ] Confirmar tabela `audit_logs` existe com colunas corretas

### 3. Configuração do MikroTik RouterOS
- [ ] Conectar via SSH/Winbox para configuração inicial
- [ ] Habilitar API: `/ip service set api address=0.0.0.0/0 port=8728`
- [ ] Criar usuário API:
  ```
  /user add name=rainet-api password=<SENHA_FORTE> group=full
  ```
- [ ] Testar conexão API:
  ```bash
  curl -X POST http://<MIKROTIK_IP>:8728/login \
    -d '{"name":"rainet-api","password":"<SENHA>"}'
  ```
- [ ] Configurar `application.yml`:
  ```yaml
  mikrotik:
    api-host: <MIKROTIK_IP>
    api-port: 8728
    api-username: rainet-api
    api-password: <SENHA>
  ```
- [ ] **IMPORTANTE**: Após primeira configuração via API, desabilitar Winbox para segurança:
  ```
  /ip service disable winbox
  ```

### 4. Configuração do Gateway PIX (Asaas)
- [ ] Obter API key no painel Asaas
- [ ] Configurar `application.yml`:
  ```yaml
  pix:
    gateway: asaas
  asaas:
    api-key: <SUA_API_KEY>
    api-url: https://api.asaas.com/v3
  app:
    webhook-url: https://seu-dominio.com/webhooks/pix
  ```
- [ ] Registrar webhook no painel Asaas
- [ ] Testar geração de QR Code PIX

### 5. Configuração RADIUS
- [ ] Instalar FreeRADIUS: `sudo apt install freeradius`
- [ ] Configurar clients.conf:
  ```
  client mikrotik {
      ipaddr = <MIKROTIK_IP>
      secret = <SECRET_FORTE>
  }
  ```
- [ ] Configurar `application.yml`:
  ```yaml
  radius:
    server: 127.0.0.1
    port: 1812
    secret: <SECRET_FORTE>
    mikrotik-rate-limit-attribute: Mikrotik-Rate-Limit
  ```
- [ ] Iniciar serviço: `sudo systemctl start freeradius`
- [ ] Testar autenticação: `radtest <usuario> <senha> localhost 0 <secret>`

### 6. Build e Deploy da Aplicação
- [ ] Compilar aplicação: `mvn clean package -DskipTests`
- [ ] Verificar JAR gerado: `ls -lh target/rainet-oss-*.jar`
- [ ] Criar serviço systemd:
  ```bash
  sudo nano /etc/systemd/system/rainet-oss.service
  ```
  Conteúdo:
  ```ini
  [Unit]
  Description=Rainet OSS/BSS Platform
  After=postgresql.service
  
  [Service]
  User=rainet
  WorkingDirectory=/opt/rainet-oss
  ExecStart=/usr/bin/java -jar /opt/rainet-oss/rainet-oss.jar
  Restart=always
  RestartSec=10
  
  [Install]
  WantedBy=multi-user.target
  ```
- [ ] Iniciar serviço: `sudo systemctl start rainet-oss`
- [ ] Verificar status: `sudo systemctl status rainet-oss`
- [ ] Habilitar boot automático: `sudo systemctl enable rainet-oss`
- [ ] Verificar logs: `sudo journalctl -u rainet-oss -f`

### 7. Configuração SSL/TLS (Nginx)
- [ ] Instalar Nginx: `sudo apt install nginx certbot python3-certbot-nginx`
- [ ] Configurar reverse proxy (`/etc/nginx/sites-available/rainet`):
  ```nginx
  server {
      listen 80;
      server_name seu-dominio.com;
      
      location / {
          proxy_pass http://localhost:8080;
          proxy_set_header Host $host;
          proxy_set_header X-Real-IP $remote_addr;
          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      }
  }
  ```
- [ ] Habilitar site: `sudo ln -s /etc/nginx/sites-available/rainet /etc/nginx/sites-enabled/`
- [ ] Obter certificado SSL: `sudo certbot --nginx -d seu-dominio.com`
- [ ] Testar renovação automática: `sudo certbot renew --dry-run`

---

## ✅ Validação Pré-Operação

### Testes de Sistema
- [ ] **Teste 1: Conectividade com MikroTik**
  - [ ] API responde corretamente
  - [ ] Script de provisioning é aplicado sem erros
  - [ ] Configuração exportada com sucesso
  - [ ] Rollback funciona corretamente

- [ ] **Teste 2: Autenticação PPPoE/RADIUS**
  - [ ] Cliente PPPoE consegue conectar
  - [ ] RADIUS autentica corretamente
  - [ ] Rate-limit é aplicado conforme plano
  - [ ] Cliente bloqueado não consegue conectar

- [ ] **Teste 3: Gateway PIX**
  - [ ] QR Code PIX é gerado corretamente
  - [ ] Webhook é recebido após pagamento
  - [ ] Invoice muda status para PAID
  - [ ] Cliente é desbloqueado automaticamente

- [ ] **Teste 4: Audit Logging**
  - [ ] Logs de provisioning são criados
  - [ ] Logs de billing são criados
  - [ ] Logs de customer são criados
  - [ ] Logs incluem IP, User-Agent, timestamp
  - [ ] Logs são imutáveis (não podem ser alterados)
  - [ ] Query de logs funciona corretamente

- [ ] **Teste 5: Multi-Tenancy**
  - [ ] Tenant A não vê dados do Tenant B
  - [ ] Queries são filtradas por tenant_id
  - [ ] Cross-tenant access é bloqueado

### Testes de Performance
- [ ] Sistema suporta 100 PPPoE simultâneos
- [ ] Latência de autenticação RADIUS < 100ms
- [ ] Provisioning completo em < 30 segundos
- [ ] Query de audit logs em < 1 segundo

### Testes de Segurança
- [ ] Winbox do MikroTik está desabilitado
- [ ] Apenas API está acessível
- [ ] JWT tokens expiram corretamente
- [ ] Senhas são hasheadas (bcrypt)
- [ ] SQL injection protegido (JPA)
- [ ] CORS configurado corretamente

---

## 🚀 Go-Live: Operação Piloto de 7 Dias

### Dia 0 (Pré-Operação)
- [ ] Reunião com equipe técnica do ISP
- [ ] Verificar todos os itens da checklist
- [ ] Backup completo do banco de dados
- [ ] Snapshot da configuração do MikroTik
- [ ] Monitoramento ativo configurado

### Dia 1: Início da Operação
- [ ] Conectar primeiro cliente teste
- [ ] Validar autenticação PPPoE
- [ ] Validar velocidade do plano
- [ ] Gerar primeira fatura
- [ ] Processar primeiro pagamento PIX
- [ ] **Verificar logs de auditoria** para todas as operações

### Dia 2-6: Operação Contínua
- [ ] Adicionar novos clientes gradualmente
- [ ] Monitorar logs de aplicação
- [ ] Verificar logs de audit_logs no banco
- [ ] Confirmar que não há intervenção manual
- [ ] Validar processamento automático de pagamentos
- [ ] Verificar bloqueio/desbloqueio automático

### Dia 7: Validação Final
- [ ] Confirmar 7 dias sem intervenção manual
- [ ] Verificar integridade dos logs de auditoria
- [ ] Validar que todos os pagamentos foram processados
- [ ] Confirmar que MikroTik não foi acessado via Winbox
- [ ] Exportar relatório de operações
- [ ] Reunião de fechamento

---

## 📊 Monitoramento Durante Operação

### Logs Críticos
- [ ] Monitorar logs da aplicação: `tail -f /var/log/rainet-oss/app.log`
- [ ] Monitorar logs do PostgreSQL
- [ ] Monitorar logs do FreeRADIUS: `/var/log/freeradius/radius.log`
- [ ] Monitorar logs do MikroTik

### Métricas de Sucesso
- [ ] **Uptime da aplicação**: > 99.9%
- [ ] **Taxa de sucesso PPPoE**: > 98%
- [ ] **Taxa de sucesso PIX**: > 95%
- [ ] **Tempo de resposta API**: < 200ms
- [ ] **Zero intervenções manuais no MikroTik**
- [ ] **100% das operações críticas auditadas**

### Queries de Auditoria
```sql
-- Verificar todas as operações de provisioning
SELECT * FROM audit_logs 
WHERE action IN ('PROVISIONING_APPLY', 'PROVISIONING_ROLLBACK') 
ORDER BY created_at DESC;

-- Verificar todas as operações de billing
SELECT * FROM audit_logs 
WHERE action IN ('BILLING_INVOICE_CREATE', 'BILLING_INVOICE_PAID') 
ORDER BY created_at DESC;

-- Verificar operações com falha
SELECT * FROM audit_logs 
WHERE status = 'FAILURE' 
ORDER BY created_at DESC;

-- Auditoria por usuário/ator
SELECT actor, action, COUNT(*) as total 
FROM audit_logs 
GROUP BY actor, action 
ORDER BY total DESC;

-- Auditoria nos últimos 7 dias
SELECT * FROM audit_logs 
WHERE created_at > NOW() - INTERVAL '7 days' 
ORDER BY created_at DESC;
```

---

## 🛡️ Critérios de Aceite

### 1. Logs Imutáveis ✅
- [x] Tabela `audit_logs` implementada com schema correto
- [x] Colunas: id, tenant_id, actor, action, resource_type, resource_id, status, payload, error_message, ip_address, user_agent, created_at
- [x] Índices criados para performance
- [x] Sem operações UPDATE permitidas
- [x] Logs incluem contexto completo da requisição

### 2. ISP Piloto Operando 7 Dias Sem Intervenção ⏳
- [ ] Sistema funcionando continuamente por 7 dias
- [ ] Zero acessos manuais ao MikroTik
- [ ] Todos os processos automatizados funcionando
- [ ] Pagamentos processados automaticamente
- [ ] Clientes bloqueados/desbloqueados automaticamente

### 3. MikroTik Configurado Sem Winbox ✅
- [ ] Configuração inicial via API
- [ ] Winbox desabilitado após configuração
- [ ] Apenas API habilitada
- [ ] Todas as mudanças via Rainet OSS

---

## 🚨 Plano de Contingência

### Se Sistema Cair
1. Verificar logs: `sudo journalctl -u rainet-oss -n 100`
2. Verificar banco: `psql rainet_oss -c "SELECT 1"`
3. Reiniciar aplicação: `sudo systemctl restart rainet-oss`
4. Se necessário, restaurar backup

### Se MikroTik Ficar Inacessível
1. Verificar conectividade de rede
2. Verificar se API está habilitada
3. Se necessário, acessar via console serial
4. Nunca usar Winbox (quebra critério de aceite)

### Se Pagamento Falhar
1. Verificar logs de webhook
2. Verificar integração com gateway PIX
3. Reprocessar manualmente se necessário
4. Registrar no audit_log

---

## 📝 Relatório Final

Após 7 dias de operação, gerar relatório incluindo:
- [ ] Total de clientes conectados
- [ ] Total de operações de provisioning
- [ ] Total de pagamentos processados
- [ ] Taxa de uptime do sistema
- [ ] Quantidade de logs de auditoria gerados
- [ ] Problemas encontrados e resolvidos
- [ ] Lições aprendidas
- [ ] Recomendações para produção

---

## ✅ Assinatura de Aprovação

**Responsável Técnico**: ____________________________  
**Data**: ____/____/____

**Responsável ISP**: ____________________________  
**Data**: ____/____/____

**Status Final**: 
- [ ] APROVADO para produção
- [ ] APROVADO com ressalvas
- [ ] NÃO APROVADO (requer melhorias)

---

**Versão do Documento**: 1.0  
**Data de Criação**: 2026-01-07  
**Última Atualização**: 2026-01-07
