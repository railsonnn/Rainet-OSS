# Integração PPPoE + FreeRADIUS

## Visão Geral

Este documento descreve a integração completa entre o Rainet OSS/BSS e FreeRADIUS para autenticação PPPoE de clientes.

## Objetivo

Autenticar clientes PPPoE via RADIUS real, com controle de banda baseado em planos e contabilização de sessões.

## Arquitetura

```
MikroTik Router (PPPoE Server)
         ↓
    FreeRADIUS (PostgreSQL backend)
         ↓
  Rainet OSS/BSS (Gestão de usuários e planos)
```

## Componentes Implementados

### 1. Banco de Dados

**Tabelas RADIUS:**
- `radcheck` - Credenciais de autenticação (username/password)
- `radreply` - Atributos de resposta (rate limits)
- `radacct` - Contabilização de sessões PPPoE
- `radpostauth` - Log de tentativas de autenticação

**Tabelas de Negócio:**
- `plans` - Planos de internet (nome, velocidade, preço)
- `customers` - Atualizado com email, password_hash, plan_id

### 2. Serviços Java

**RadiusUserService:**
- Cria/atualiza usuários RADIUS nas tabelas radcheck/radreply
- Gerencia rate limits MikroTik
- Bloqueia/desbloqueia clientes

**RadiusAccountingService:**
- Consulta sessões PPPoE ativas
- Histórico de sessões por usuário
- Estatísticas de uso (tempo, tráfego)

**RadiusServerService:**
- Autenticação de clientes via RADIUS (legado)
- Validação de status e plano
- Retorno de atributos MikroTik

### 3. API REST

**Endpoints:**

```bash
# Criar usuário RADIUS
POST /api/v1/radius/users
{
  "customerId": "uuid",
  "password": "senha123"
}

# Remover usuário RADIUS
DELETE /api/v1/radius/users/{username}

# Atualizar rate limit
PUT /api/v1/radius/users/{customerId}/rate-limit

# Bloquear cliente
POST /api/v1/radius/users/{customerId}/block

# Desbloquear cliente
POST /api/v1/radius/users/{customerId}/unblock

# Listar sessões ativas
GET /api/v1/radius/sessions

# Sessões ativas por usuário
GET /api/v1/radius/sessions/user/{username}

# Histórico de sessões
GET /api/v1/radius/sessions/user/{username}/history?limit=10

# Estatísticas de uso
GET /api/v1/radius/sessions/user/{username}/stats
```

## Configuração

### 1. Docker Compose

O `docker-compose.yml` inclui o serviço FreeRADIUS:

```yaml
freeradius:
  image: freeradius/freeradius-server:latest
  ports:
    - "1812:1812/udp"  # Authentication
    - "1813:1813/udp"  # Accounting
  environment:
    DB_HOST: db
    DB_NAME: rainet
    DB_USER: rainet
    DB_PASSWORD: rainet
    RADIUS_SECRET: testing123
```

### 2. MikroTik Configuration

Configure o MikroTik para usar FreeRADIUS:

```routeros
# Adicionar servidor RADIUS
/radius add service=ppp address=<freeradius-ip> secret=testing123

# Ativar RADIUS no PPP
/ppp aaa set use-radius=yes accounting=yes interim-update=1m

# Configurar PPPoE Server
/interface pppoe-server server
set enabled=yes service-name=rainet-isp one-session-per-host=yes
```

### 3. Application Properties

```yaml
radius:
  server: freeradius
  port: 1812
  secret: testing123
  mikrotik-rate-limit-attribute: Mikrotik-Rate-Limit
```

## Fluxo de Autenticação

1. **Cliente conecta via PPPoE no MikroTik**
   - Username: cliente@example.com
   - Password: senha123

2. **MikroTik envia Access-Request para FreeRADIUS**
   - FreeRADIUS consulta `radcheck` para validar credenciais
   - FreeRADIUS busca atributos em `radreply` (rate limits)

3. **FreeRADIUS retorna Access-Accept com atributos**
   - `Mikrotik-Rate-Limit`: "10000000/20000000" (10Mbps up / 20Mbps down)
   - Cliente autenticado e banda aplicada

4. **Accounting ativo**
   - MikroTik envia Accounting-Start ao conectar
   - Accounting-Update a cada 1 minuto
   - Accounting-Stop ao desconectar
   - Dados gravados em `radacct`

## Exemplo de Uso

### Criar Cliente com PPPoE

```bash
# 1. Criar plano (se não existir)
# Planos são criados automaticamente na migration V2

# 2. Criar cliente
POST /customers
{
  "fullName": "João Silva",
  "document": "12345678900",
  "email": "joao@example.com",
  "planId": "uuid-do-plano-basic",
  "status": "ACTIVE"
}

# 3. Criar usuário RADIUS
POST /api/v1/radius/users
{
  "customerId": "uuid-do-cliente",
  "password": "senha123"
}

# 4. Cliente pode conectar via PPPoE
# Username: joao@example.com
# Password: senha123
```

### Bloquear Cliente por Inadimplência

```bash
POST /api/v1/radius/users/{customerId}/block
```

Resultado:
- Rate limit alterado para 1kbps (navegação impossível)
- Cliente continua autenticando, mas sem acesso efetivo

### Desbloquear Cliente após Pagamento

```bash
POST /api/v1/radius/users/{customerId}/unblock
```

Resultado:
- Rate limit restaurado para o plano original
- Cliente volta a ter acesso normal

## Critérios de Aceite

✅ **Cliente conecta via PPPoE**
- Cliente se autentica com email/senha
- FreeRADIUS valida contra PostgreSQL
- Sessão estabelecida

✅ **Banda aplicada corretamente**
- Rate limit vem do plano do cliente
- MikroTik recebe atributo `Mikrotik-Rate-Limit`
- Banda enforced em tempo real

✅ **Sessão contabilizada**
- Início de sessão gravado em `radacct`
- Updates a cada 1 minuto
- Fim de sessão registrado com tráfego total
- Estatísticas disponíveis via API

## Testes

### Teste Manual com radtest

```bash
# Instalar radtest
apt-get install freeradius-utils

# Testar autenticação
radtest joao@example.com senha123 localhost 1812 testing123
```

Resposta esperada:
```
Received Access-Accept Id 123
    Mikrotik-Rate-Limit = "10000000/20000000"
```

### Consultar Sessão Ativa

```bash
curl http://localhost:8080/api/v1/radius/sessions
```

Resposta:
```json
[
  {
    "radacctid": 1,
    "username": "joao@example.com",
    "nasipaddress": "192.168.1.1",
    "acctstarttime": "2024-01-07T10:30:00",
    "acctsessiontime": 3600,
    "acctinputoctets": 104857600,
    "acctoutputoctets": 524288000,
    "framedipaddress": "10.0.1.100",
    "active": true
  }
]
```

## Monitoramento

### Queries Úteis

```sql
-- Sessões ativas
SELECT username, nasipaddress, framedipaddress, acctstarttime 
FROM radacct 
WHERE acctstoptime IS NULL;

-- Top 10 usuários por tráfego
SELECT username, 
       SUM(acctinputoctets + acctoutputoctets) / 1024 / 1024 / 1024 as total_gb
FROM radacct 
GROUP BY username 
ORDER BY total_gb DESC 
LIMIT 10;

-- Tentativas de autenticação falhadas
SELECT username, COUNT(*) as attempts 
FROM radpostauth 
WHERE reply = 'Access-Reject' 
AND authdate > NOW() - INTERVAL '1 hour'
GROUP BY username;
```

## Troubleshooting

### Cliente não autentica

1. Verificar se usuário existe em `radcheck`:
```sql
SELECT * FROM radcheck WHERE username = 'cliente@example.com';
```

2. Verificar logs do FreeRADIUS:
```bash
docker-compose logs freeradius
```

3. Testar com radtest (ver acima)

### Banda não está sendo aplicada

1. Verificar atributos em `radreply`:
```sql
SELECT * FROM radreply WHERE username = 'cliente@example.com';
```

2. Verificar atributo `Mikrotik-Rate-Limit` no Access-Accept

3. Verificar configuração do MikroTik:
```routeros
/ppp aaa print
# use-radius deve estar yes
```

### Accounting não funciona

1. Verificar se FreeRADIUS está escutando porta 1813:
```bash
netstat -ulnp | grep 1813
```

2. Verificar configuração do MikroTik:
```routeros
/ppp aaa print
# accounting deve estar yes
```

3. Verificar tabela radacct:
```sql
SELECT COUNT(*) FROM radacct;
```

## Próximos Passos

- [ ] Implementar bcrypt para passwords (atualmente Cleartext-Password)
- [ ] Adicionar suporte a CHAP/MS-CHAP
- [ ] Dashboard de sessões ativas em tempo real
- [ ] Alertas de uso excessivo de banda
- [ ] Relatórios de faturamento baseados em uso
- [ ] Integração com billing para bloqueio automático

## Referências

- [FreeRADIUS Documentation](https://freeradius.org/documentation/)
- [MikroTik RADIUS](https://wiki.mikrotik.com/wiki/Manual:RADIUS_Client)
- [PostgreSQL RADIUS Schema](https://github.com/FreeRADIUS/freeradius-server/tree/master/raddb/mods-config/sql/main/postgresql)
