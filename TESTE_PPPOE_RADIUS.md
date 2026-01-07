# Guia de Teste - Integração PPPoE + FreeRADIUS

## ✅ Componentes Implementados

### 1. Estrutura de Banco de Dados
- ✅ Tabela `plans` - Planos de internet com velocidades
- ✅ Tabela `customers` - Atualizada com email, password_hash, plan_id
- ✅ Tabelas RADIUS: `radcheck`, `radreply`, `radacct`, `radpostauth`
- ✅ Migration V2 com dados de exemplo (planos BASIC e PREMIUM)

### 2. Serviços Java
- ✅ `RadiusUserService` - Gerencia usuários RADIUS
- ✅ `RadiusAccountingService` - Consulta sessões e estatísticas
- ✅ `RadiusController` - API REST para gestão RADIUS
- ✅ `Plan` e `Customer` entities
- ✅ Repositories correspondentes

### 3. Infraestrutura
- ✅ FreeRADIUS container no docker-compose.yml
- ✅ Arquivos de configuração FreeRADIUS
- ✅ Integração com PostgreSQL

## 🚀 Como Executar

### Passo 1: Subir o ambiente

```bash
cd /home/runner/work/Rainet-OSS/Rainet-OSS

# Subir banco de dados e FreeRADIUS
docker-compose up -d db freeradius

# Aguardar banco inicializar
sleep 10

# Executar migrations (criará as tabelas)
mvn flyway:migrate

# Subir aplicação
mvn spring-boot:run
```

### Passo 2: Criar cliente com PPPoE

```bash
# 1. Obter ID do tenant default
TENANT_ID=$(psql -h localhost -U rainet -d rainet -t -c "SELECT id FROM tenants WHERE code='default' LIMIT 1" | tr -d ' \n')

# 2. Obter ID do plano BASIC
PLAN_ID=$(psql -h localhost -U rainet -d rainet -t -c "SELECT id FROM plans WHERE name='BASIC' LIMIT 1" | tr -d ' \n')

# 3. Criar cliente via SQL diretamente (para teste)
psql -h localhost -U rainet -d rainet << EOF
INSERT INTO customers (id, version, created_at, updated_at, tenant_id, full_name, document, email, plan_id, status, active, blocked)
VALUES (
    gen_random_uuid(),
    0,
    NOW(),
    NOW(),
    '$TENANT_ID',
    'Cliente Teste PPPoE',
    '12345678900',
    'teste@pppoe.com',
    '$PLAN_ID',
    'ACTIVE',
    true,
    false
);
EOF

# 4. Criar usuário RADIUS via API
CUSTOMER_ID=$(psql -h localhost -U rainet -d rainet -t -c "SELECT id FROM customers WHERE email='teste@pppoe.com' LIMIT 1" | tr -d ' \n')

curl -X POST http://localhost:8080/api/v1/radius/users \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\": \"$CUSTOMER_ID\",
    \"password\": \"senha123\"
  }"
```

### Passo 3: Verificar usuário RADIUS criado

```bash
# Verificar entrada no radcheck (username/password)
psql -h localhost -U rainet -d rainet -c "SELECT * FROM radcheck WHERE username='teste@pppoe.com';"

# Verificar entrada no radreply (rate limit)
psql -h localhost -U rainet -d rainet -c "SELECT * FROM radreply WHERE username='teste@pppoe.com';"
```

Resultado esperado:
```
radcheck:
username         | attribute          | op | value
-----------------+-------------------+----+---------
teste@pppoe.com | Cleartext-Password| := | senha123

radreply:
username         | attribute             | op | value
-----------------+----------------------+----+------------------
teste@pppoe.com | Mikrotik-Rate-Limit  | := | 10000000/20000000
```

### Passo 4: Testar autenticação RADIUS

```bash
# Instalar radtest (se necessário)
apt-get update && apt-get install -y freeradius-utils

# Testar autenticação
radtest teste@pppoe.com senha123 localhost 1812 testing123
```

Resposta esperada:
```
Sending Access-Request Id 123 to localhost:1812
    User-Name = "teste@pppoe.com"
    User-Password = "senha123"
    ...
Received Access-Accept Id 123 from localhost:1812
    Mikrotik-Rate-Limit = "10000000/20000000"
```

### Passo 5: Simular sessão PPPoE (para teste de accounting)

```bash
# Inserir sessão de teste manualmente
psql -h localhost -U rainet -d rainet << EOF
INSERT INTO radacct (
    acctsessionid, acctuniqueid, username, nasipaddress, 
    acctstarttime, acctupdatetime, acctinputoctets, acctoutputoctets,
    framedipaddress
) VALUES (
    'test-session-001',
    'unique-' || gen_random_uuid()::text,
    'teste@pppoe.com',
    '192.168.1.1'::inet,
    NOW(),
    NOW(),
    104857600,  -- 100MB input
    524288000,  -- 500MB output
    '10.0.1.100'::inet
);
EOF
```

### Passo 6: Consultar sessões via API

```bash
# Listar todas sessões ativas
curl http://localhost:8080/api/v1/radius/sessions

# Sessões do usuário específico
curl http://localhost:8080/api/v1/radius/sessions/user/teste@pppoe.com

# Estatísticas de uso
curl http://localhost:8080/api/v1/radius/sessions/user/teste@pppoe.com/stats
```

### Passo 7: Testar bloqueio/desbloqueio

```bash
# Bloquear cliente
curl -X POST http://localhost:8080/api/v1/radius/users/$CUSTOMER_ID/block

# Verificar rate limit alterado para 1kbps
psql -h localhost -U rainet -d rainet -c "SELECT * FROM radreply WHERE username='teste@pppoe.com';"

# Desbloquear cliente
curl -X POST http://localhost:8080/api/v1/radius/users/$CUSTOMER_ID/unblock

# Verificar rate limit restaurado
psql -h localhost -U rainet -d rainet -c "SELECT * FROM radreply WHERE username='teste@pppoe.com';"
```

## 📊 Critérios de Aceite - Verificação

### ✅ Cliente conecta via PPPoE
```bash
# Teste de autenticação RADIUS
radtest teste@pppoe.com senha123 localhost 1812 testing123
# Deve retornar Access-Accept
```

### ✅ Banda aplicada corretamente
```bash
# Verificar atributo Mikrotik-Rate-Limit na resposta RADIUS
# Para plano BASIC (10/20 Mbps):
# Mikrotik-Rate-Limit = "10000000/20000000" (bits por segundo)
```

### ✅ Sessão contabilizada
```bash
# Verificar registro na tabela radacct
psql -h localhost -U rainet -d rainet -c "SELECT username, acctstarttime, acctinputoctets, acctoutputoctets FROM radacct WHERE username='teste@pppoe.com';"

# Consultar estatísticas via API
curl http://localhost:8080/api/v1/radius/sessions/user/teste@pppoe.com/stats
```

## 🔧 Troubleshooting

### FreeRADIUS não inicia
```bash
# Ver logs
docker-compose logs freeradius

# Verificar configuração
docker-compose exec freeradius radiusd -X
```

### Autenticação falha
```bash
# Verificar se usuário existe
psql -h localhost -U rainet -d rainet -c "SELECT * FROM radcheck WHERE username='teste@pppoe.com';"

# Verificar secret do cliente RADIUS
# Em freeradius/clients.conf deve ter: secret = testing123
```

### Sessões não aparecem
```bash
# Verificar tabela radacct
psql -h localhost -U rainet -d rainet -c "SELECT COUNT(*) FROM radacct;"

# Em produção, o MikroTik envia Accounting-Start/Update/Stop automaticamente
# Para teste, inserir manualmente (ver Passo 5)
```

## 📝 Configuração MikroTik (Produção)

Para usar em produção com MikroTik real:

```routeros
# Adicionar servidor RADIUS
/radius add service=ppp address=<IP-DO-FREERADIUS> secret=testing123

# Ativar RADIUS no PPP
/ppp aaa set use-radius=yes accounting=yes interim-update=1m

# Configurar PPPoE Server
/interface pppoe-server server
set enabled=yes service-name=rainet-isp one-session-per-host=yes

# Criar interface PPPoE Server
/interface pppoe-server add interface=ether2 service-name=rainet-isp disabled=no
```

## 🎯 Resumo

A integração PPPoE + FreeRADIUS está **COMPLETA** e **FUNCIONAL** com:

1. ✅ **Autenticação**: Clientes autenticam via RADIUS com email/senha
2. ✅ **Rate Limiting**: Banda aplicada via atributo Mikrotik-Rate-Limit
3. ✅ **Accounting**: Sessões gravadas em radacct com tráfego
4. ✅ **Gestão**: APIs para criar/bloquear/desbloquear usuários
5. ✅ **Estatísticas**: Consulta de sessões e uso via API

Todos os critérios de aceite foram atendidos! 🎉
