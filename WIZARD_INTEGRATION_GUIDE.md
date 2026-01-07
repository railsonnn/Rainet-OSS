# Integração do Gerador de Scripts RouterOS com Wizard

## Visão Geral

Este documento descreve como integrar o gerador modular de scripts RouterOS com uma interface wizard (assistente de configuração) web.

## Arquitetura

```
[Wizard UI] → [RouterOsScriptGenerator] → [RouterOsScriptBuilder] → [8 Section Builders]
     ↓                                              ↓
[RouterOsConfig DTO]                        [Script RouterOS]
```

## Fluxo do Wizard

### Passo 1: Informações Básicas
- Nome do roteador
- Endereço de gerenciamento
- Modelo (RB/CCR)
- Versão RouterOS

### Passo 2: Configuração WAN
- Interface WAN (padrão: wan/ether1)
- Tipo de conexão:
  - DHCP Client (automático)
  - IP Estático (requer: IP, Gateway, Máscara)
- DNS primário e secundário

### Passo 3: Configuração LAN
- Interface LAN (padrão: lan/ether2)
- Rede LAN (ex: 192.168.88.1/24)
- Bridge LAN
- Servidor DHCP:
  - Pool de IPs
  - Gateway
  - DNS

### Passo 4: PPPoE Server
- Habilitar PPPoE: Sim/Não
- Service Name (ex: rainet-isp)
- Planos de serviço (múltiplos):
  - Nome do plano
  - Velocidade Upload (Mbps)
  - Velocidade Download (Mbps)
  - Pool de IPs (prefix: 10.10.x)
  - Tamanho do pool

### Passo 5: RADIUS
- Servidor RADIUS (IP)
- Segredo compartilhado
- Timeout (padrão: 3s)
- Accounting: Sim/Não
- Interim update: 1m

### Passo 6: Firewall
- Habilitar firewall: Sim/Não
- NAT: Sim/Não
- Regras customizadas (opcional):
  - Chain (input/forward/output)
  - Action (accept/drop/reject)
  - Protocolo
  - Porta origem/destino
  - Endereço origem/destino

### Passo 7: QoS
- Habilitar QoS: Sim/Não
- Bandwidth global (Mbps)
- Perfis de QoS (múltiplos):
  - Nome
  - Prioridade (1-8)
  - Bandwidth (Mbps)

### Passo 8: Revisão e Geração
- Mostrar resumo da configuração
- Opções:
  - Gerar e baixar script
  - Aplicar diretamente via API
  - Salvar como snapshot

## Exemplo de Integração - Spring MVC Controller

```java
@RestController
@RequestMapping("/api/wizard")
public class RouterWizardController {

    @Autowired
    private RouterOsScriptGenerator scriptGenerator;
    
    @Autowired
    private RouterRepository routerRepository;
    
    @Autowired
    private ProvisioningService provisioningService;

    /**
     * POST /api/wizard/generate
     * Gera script RouterOS a partir do formulário do wizard
     */
    @PostMapping("/generate")
    public ResponseEntity<WizardResponse> generateScript(@RequestBody WizardRequest request) {
        // 1. Criar ou buscar Router
        Router router = findOrCreateRouter(request);
        
        // 2. Construir RouterOsConfig do request do wizard
        RouterOsConfig config = buildConfigFromWizard(request);
        
        // 3. Gerar script
        String script = scriptGenerator.buildScript(router, config);
        
        // 4. Retornar resposta
        WizardResponse response = new WizardResponse();
        response.setScript(script);
        response.setRouterName(router.getHostname());
        response.setTimestamp(LocalDateTime.now());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * POST /api/wizard/apply
     * Aplica configuração diretamente no roteador via API
     */
    @PostMapping("/apply")
    public ResponseEntity<ApplyResponse> applyScript(@RequestBody WizardRequest request) {
        Router router = findOrCreateRouter(request);
        RouterOsConfig config = buildConfigFromWizard(request);
        String script = scriptGenerator.buildScript(router, config);
        
        // Aplicar via ProvisioningService
        UUID snapshotId = provisioningService.apply(
            new ProvisioningRequest(router.getId(), "Wizard configuration"),
            SecurityContextHolder.getContext().getAuthentication().getName()
        );
        
        ApplyResponse response = new ApplyResponse();
        response.setSuccess(true);
        response.setSnapshotId(snapshotId);
        response.setMessage("Configuração aplicada com sucesso");
        
        return ResponseEntity.ok(response);
    }
    
    private RouterOsConfig buildConfigFromWizard(WizardRequest request) {
        return RouterOsConfig.builder()
                .version("1.0")
                .routerName(request.getRouterName())
                // WAN
                .wanInterface(request.getWanInterface())
                .wanAddress(request.getWanAddress())
                .wanGateway(request.getWanGateway())
                // LAN
                .lanInterface(request.getLanInterface())
                .bridgeInterface(request.getBridgeInterface())
                .lanNetwork(request.getLanNetwork())
                .lanDns1(request.getDns1())
                .lanDns2(request.getDns2())
                // PPPoE
                .pppoeEnabled(request.isPppoeEnabled())
                .pppoeService(request.getPppoeService())
                .pppePlans(convertPPPoEPlans(request.getPppePlans()))
                .radiusServer(request.getRadiusServer())
                .radiusSecret(request.getRadiusSecret())
                // QoS
                .qosEnabled(request.isQosEnabled())
                .defaultBandwidthMbps(request.getDefaultBandwidth())
                .qosProfiles(convertQoSProfiles(request.getQosProfiles()))
                // Firewall
                .firewallEnabled(request.isFirewallEnabled())
                .natEnabled(request.isNatEnabled())
                .customRules(convertFirewallRules(request.getCustomRules()))
                .build();
    }
}
```

## Exemplo de DTO - Request do Wizard

```java
@Data
public class WizardRequest {
    // Básico
    private String routerName;
    private String managementIp;
    private String routerModel;
    
    // WAN
    private String wanInterface = "wan";
    private String wanAddress; // "dhcp-client" ou IP/mask
    private String wanGateway;
    
    // LAN
    private String lanInterface = "lan";
    private String bridgeInterface = "bridge-lan";
    private String lanNetwork;
    private String dns1;
    private String dns2;
    
    // PPPoE
    private boolean pppoeEnabled;
    private String pppoeService;
    private List<WizardPPPoEPlan> pppePlans;
    private String radiusServer;
    private String radiusSecret;
    
    // QoS
    private boolean qosEnabled;
    private int defaultBandwidth;
    private List<WizardQoSProfile> qosProfiles;
    
    // Firewall
    private boolean firewallEnabled;
    private boolean natEnabled;
    private List<WizardFirewallRule> customRules;
    
    @Data
    public static class WizardPPPoEPlan {
        private String name;
        private String poolPrefix;
        private int uploadMbps;
        private int downloadMbps;
    }
    
    @Data
    public static class WizardQoSProfile {
        private String name;
        private int priority;
        private int bandwidthMbps;
    }
    
    @Data
    public static class WizardFirewallRule {
        private String chain;
        private String action;
        private String protocol;
        private String srcAddress;
        private String dstAddress;
        private Integer srcPort;
        private Integer dstPort;
        private String comment;
    }
}
```

## Exemplo de UI - React/TypeScript

```typescript
interface WizardStep {
  title: string;
  component: React.ComponentType<any>;
}

const RouterWizard: React.FC = () => {
  const [step, setStep] = useState(0);
  const [config, setConfig] = useState<WizardConfig>({});
  
  const steps: WizardStep[] = [
    { title: "Informações Básicas", component: BasicInfoStep },
    { title: "Configuração WAN", component: WanConfigStep },
    { title: "Configuração LAN", component: LanConfigStep },
    { title: "Servidor PPPoE", component: PppoeConfigStep },
    { title: "RADIUS", component: RadiusConfigStep },
    { title: "Firewall", component: FirewallConfigStep },
    { title: "QoS", component: QosConfigStep },
    { title: "Revisão", component: ReviewStep },
  ];
  
  const handleNext = () => setStep(step + 1);
  const handleBack = () => setStep(step - 1);
  
  const handleGenerate = async () => {
    try {
      const response = await fetch('/api/wizard/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      });
      
      const data = await response.json();
      
      // Download do script
      const blob = new Blob([data.script], { type: 'text/plain' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${config.routerName}_config.rsc`;
      a.click();
      
      // Mostrar sucesso
      toast.success('Script gerado com sucesso!');
    } catch (error) {
      toast.error('Erro ao gerar script');
    }
  };
  
  return (
    <div className="wizard-container">
      <WizardStepper steps={steps} currentStep={step} />
      <div className="wizard-content">
        {React.createElement(steps[step].component, { 
          config, 
          setConfig 
        })}
      </div>
      <div className="wizard-actions">
        {step > 0 && (
          <Button onClick={handleBack}>Voltar</Button>
        )}
        {step < steps.length - 1 && (
          <Button onClick={handleNext}>Próximo</Button>
        )}
        {step === steps.length - 1 && (
          <Button onClick={handleGenerate} variant="primary">
            Gerar Script
          </Button>
        )}
      </div>
    </div>
  );
};
```

## Validações do Wizard

### Validações no Frontend
```typescript
const validateBasicInfo = (data: BasicInfo): ValidationResult => {
  const errors: string[] = [];
  
  if (!data.routerName || data.routerName.length < 3) {
    errors.push("Nome do roteador deve ter pelo menos 3 caracteres");
  }
  
  if (!isValidIP(data.managementIp)) {
    errors.push("IP de gerenciamento inválido");
  }
  
  return { valid: errors.length === 0, errors };
};

const validateLanConfig = (data: LanConfig): ValidationResult => {
  const errors: string[] = [];
  
  if (!isValidCIDR(data.lanNetwork)) {
    errors.push("Rede LAN deve estar no formato IP/máscara (ex: 192.168.1.1/24)");
  }
  
  if (data.lanNetwork && !isPrivateIP(data.lanNetwork)) {
    errors.push("Rede LAN deve usar endereços IP privados");
  }
  
  return { valid: errors.length === 0, errors };
};
```

### Validações no Backend
```java
@Component
public class WizardConfigValidator {
    
    public void validate(WizardRequest request) throws ValidationException {
        validateBasicInfo(request);
        validateWanConfig(request);
        validateLanConfig(request);
        validatePppoeConfig(request);
        validateRadiusConfig(request);
        validateFirewallConfig(request);
        validateQosConfig(request);
    }
    
    private void validateLanConfig(WizardRequest request) {
        if (!isValidCIDR(request.getLanNetwork())) {
            throw new ValidationException("Rede LAN inválida");
        }
        
        if (!isPrivateNetwork(request.getLanNetwork())) {
            throw new ValidationException("Rede LAN deve usar IPs privados");
        }
        
        // Verificar conflito com rede WAN
        if (networksOverlap(request.getWanAddress(), request.getLanNetwork())) {
            throw new ValidationException("Rede LAN não pode sobrepor rede WAN");
        }
    }
    
    private void validatePppoeConfig(WizardRequest request) {
        if (request.isPppoeEnabled()) {
            if (request.getPppePlans() == null || request.getPppePlans().isEmpty()) {
                throw new ValidationException("PPPoE habilitado mas sem planos definidos");
            }
            
            // Verificar pools únicos
            Set<String> pools = new HashSet<>();
            for (var plan : request.getPppePlans()) {
                if (!pools.add(plan.getPoolPrefix())) {
                    throw new ValidationException("Pool prefix duplicado: " + plan.getPoolPrefix());
                }
            }
        }
    }
}
```

## Segurança

### 1. Autenticação e Autorização
```java
@PreAuthorize("hasRole('TECH') or hasRole('ADMIN')")
@PostMapping("/generate")
public ResponseEntity<WizardResponse> generateScript(@RequestBody WizardRequest request) {
    // Verificar tenant
    UUID tenantId = TenantContext.getCurrentTenant();
    
    // Apenas usuários do mesmo tenant podem gerar scripts
    // ...
}
```

### 2. Sanitização de Inputs
```java
public class SecurityUtils {
    
    public static String sanitizeRouterName(String name) {
        // Remover caracteres especiais que possam causar injection
        return name.replaceAll("[^a-zA-Z0-9-_]", "");
    }
    
    public static String sanitizeComment(String comment) {
        // Escapar aspas e caracteres especiais
        return comment.replace("\"", "\\\"").replace("\n", " ");
    }
}
```

### 3. Rate Limiting
```java
@RateLimiter(name = "wizard", fallbackMethod = "rateLimitFallback")
@PostMapping("/generate")
public ResponseEntity<WizardResponse> generateScript(@RequestBody WizardRequest request) {
    // Limitar a 10 gerações por minuto por usuário
}
```

## Testes

### Teste de Integração
```java
@SpringBootTest
@AutoConfigureMockMvc
class RouterWizardControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldGenerateScriptFromWizard() throws Exception {
        WizardRequest request = createValidWizardRequest();
        
        mockMvc.perform(post("/api/wizard/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.script").isNotEmpty())
                .andExpect(jsonPath("$.script").value(containsString("# Rainet OSS")))
                .andExpect(jsonPath("$.script").value(containsString("/interface/bridge")))
                .andExpect(jsonPath("$.script").value(containsString("/ppp/profile")));
    }
    
    @Test
    void shouldValidateInvalidLanNetwork() throws Exception {
        WizardRequest request = createValidWizardRequest();
        request.setLanNetwork("invalid");
        
        mockMvc.perform(post("/api/wizard/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Rede LAN inválida")));
    }
}
```

## Próximos Passos

1. **Implementar frontend do wizard** com React/Angular/Vue
2. **Adicionar preview em tempo real** do script conforme o usuário preenche
3. **Implementar salvamento de templates** para configurações reutilizáveis
4. **Criar assistente inteligente** com sugestões baseadas no cenário
5. **Adicionar validação de compatibilidade** com versão RouterOS
6. **Implementar diff visual** para mostrar mudanças entre versões
7. **Criar biblioteca de snippets** para regras firewall comuns

## Referências

- [MikroTik RouterOS Documentation](https://help.mikrotik.com/docs/)
- [RouterOS Scripting](https://wiki.mikrotik.com/wiki/Manual:Scripting)
- [PPPoE Server Setup](https://wiki.mikrotik.com/wiki/PPPoE)
- [RADIUS Configuration](https://wiki.mikrotik.com/wiki/Manual:RADIUS)
