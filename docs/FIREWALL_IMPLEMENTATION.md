# Implementação do Firewall Básico e NAT Automático

## Objetivo

Garantir conectividade e segurança mínima no piloto através de regras de firewall e NAT automático no MikroTik RouterOS.

## Escopo Técnico Implementado

### 1. Drop Invalid Connections ✅
- Descarta pacotes com estado de conexão inválido tanto na chain `input` quanto na `forward`
- Protege contra pacotes malformados ou fora de sequência

```routeros
add action=drop chain=input connection-state=invalid comment="Drop invalid input"
add action=drop chain=forward connection-state=invalid comment="Drop invalid forward"
```

### 2. Accept Established/Related ✅
- Aceita conexões estabelecidas e relacionadas
- Permite que conexões legítimas continuem sem overhead adicional
- Implementado para ambas as chains `input` e `forward`

```routeros
add action=accept chain=input connection-state=established,related comment="Accept established/related input"
add action=accept chain=forward connection-state=established,related comment="Accept established/related forward"
```

### 3. NAT Masquerade ✅
- Mascaramento automático para tráfego saindo pela interface WAN
- Permite que múltiplos clientes compartilhem um único IP público
- Essencial para fornecer internet aos clientes

```routeros
/ip/firewall/nat
add action=masquerade chain=srcnat out-interface=ether1 comment="Masquerade WAN"
```

### 4. Proteção Básica Contra Flood ✅

#### 4.1 SYN Flood Protection
Limita novas conexões TCP por IP para prevenir ataques SYN flood:
```routeros
add action=drop chain=input protocol=tcp tcp-flags=syn connection-limit=30,32 comment="Drop SYN flood"
```

#### 4.2 Connection Rate Limiting
Rastreia e limita a taxa de novas conexões por IP:
```routeros
add action=add-src-to-address-list chain=input connection-state=new src-address-list=connection_limit address-list-timeout=1m comment="Track new connections"
add action=drop chain=input src-address-list=connection_limit connection-state=new connection-limit=20,32 comment="Drop connection flood"
```

#### 4.3 Port Scan Detection
Detecta e bloqueia tentativas de varredura de portas por 24 horas:
```routeros
add action=add-src-to-address-list chain=input protocol=tcp psd=21,3s,3,1 address-list=port_scanners address-list-timeout=1d comment="Detect port scan"
add action=drop chain=input src-address-list=port_scanners comment="Drop port scanners"
```

#### 4.4 ICMP Rate Limiting
Limita pacotes ICMP para 5 por segundo para prevenir ICMP flood:
```routeros
add action=accept chain=input protocol=icmp limit=5,5:packet comment="Accept limited ICMP"
add action=drop chain=input protocol=icmp comment="Drop ICMP flood"
```

## Critérios de Aceite

### ✅ Internet Funcional
- Tráfego da LAN (ether2+) para WAN (ether1) é permitido
- NAT masquerade configurado para permitir acesso à internet
- Clientes receberão conectividade normal

### ✅ Router Protegido
- Proteção contra SYN flood ativa
- Proteção contra connection flood ativa
- Detecção de port scan implementada
- Proteção contra ICMP flood ativa
- Pacotes inválidos são descartados

### ✅ Sem Regras Duplicadas
- Cada regra aparece apenas uma vez no script
- Uso de comments distintos para cada regra
- Script idempotente - pode ser reaplicado sem criar duplicatas

## Ordem de Avaliação das Regras

As regras são avaliadas na seguinte ordem (importante para performance e segurança):

1. **Drop invalid** - Descarta imediatamente pacotes inválidos
2. **Flood protection** - Protege contra ataques antes de processar tráfego legítimo
3. **Accept established/related** - Permite conexões existentes rapidamente
4. **Accept from LAN** - Permite tráfego da rede local
5. **Accept from local interfaces** - Permite acesso de interfaces locais ao router
6. **Drop other** - Política padrão de deny (segurança em profundidade)

## Connection Tracking

O connection tracking está habilitado com timeouts otimizados:
- TCP timeout: 23 horas
- UDP timeout: 10 minutos

```routeros
/ip/firewall/connection-tracking
set enabled=yes
set tcp-timeout=23h
set udp-timeout=10m
```

## Testes Implementados

### Unit Tests (`FirewallSectionBuilderTest.java`)

1. ✅ Verifica mensagem quando firewall está desabilitado
2. ✅ Valida configuração de connection tracking
3. ✅ Verifica drop de conexões inválidas
4. ✅ Valida proteção contra SYN flood
5. ✅ Verifica rate limiting de conexões
6. ✅ Valida detecção de port scan
7. ✅ Verifica rate limiting de ICMP
8. ✅ Valida accept de conexões established/related
9. ✅ Verifica accept de tráfego da LAN
10. ✅ Valida NAT masquerade quando habilitado
11. ✅ Verifica ausência de NAT quando desabilitado
12. ✅ Garante ausência de regras duplicadas
13. ✅ Valida ordem correta das regras

## Configuração

Para habilitar o firewall e NAT no script de provisionamento:

```java
RouterOsConfig config = RouterOsConfig.builder()
    .firewallEnabled(true)  // Habilita firewall com flood protection
    .natEnabled(true)        // Habilita NAT masquerade
    // ... outras configurações
    .build();
```

## Integração com RouterOS

O script gerado pode ser aplicado via:
- MikroTik API (RouterOsApiExecutor)
- SSH com comando `/import`
- Web interface do RouterOS

## Notas de Segurança

1. **Não remova as regras de flood protection** - Elas protegem contra ataques comuns
2. **Mantenha a ordem das regras** - A ordem é crítica para performance e segurança
3. **Monitore address-lists** - `connection_limit` e `port_scanners` podem crescer
4. **Ajuste limites conforme necessário** - Os valores são adequados para a maioria dos cenários, mas podem ser ajustados

## Próximos Passos

Após este piloto, considere adicionar:
- Geolocalização de IPs para bloqueio de países específicos
- Rate limiting para serviços específicos (SSH, API, etc.)
- Logging de tentativas de ataque
- Alertas automáticos via email/Telegram
- Blacklist/whitelist de IPs

## Referências

- [MikroTik Wiki - Firewall](https://wiki.mikrotik.com/wiki/Manual:IP/Firewall)
- [MikroTik Best Practices](https://wiki.mikrotik.com/wiki/Manual:Securing_Your_Router)
- Classe: `com.isp.platform.provisioning.mikrotik.builder.FirewallSectionBuilder`
