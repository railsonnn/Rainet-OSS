# Rainet-OSS

## Status de implementação (atualizado em 2026-01-07)

Este documento sumariza o status das principais tasks do projeto — indica se a funcionalidade já está implementada no código do repositório ou se permanece como stub/draft.

- ✅ Task 1 — Conexão real com MikroTik API (Execução real)
  - Conclusão: ❌ Não atendida
  - Observação: O README e o código indicam que a funcionalidade ainda está "stubbed"; não há evidência de código que aplique configurações reais via RouterOS API.

- ✅ Task 2 — Gerador modular de script RouterOS
  - Conclusão: ❌ Não atendida
  - Observação: Intenção documentada, mas não há builders modulares nem lógica para geração de .rsc claramente implementada.

- ✅ Task 3 — Snapshot BEFORE / AFTER
  - Conclusão: ❌ Não atendida
  - Observação: Endpoint referido no README; não há implementação funcional demonstrável no código.

- ✅ Task 4 — Rollback funcional real
  - Conclusão: ❌ Não atendida
  - Observação: Endpoint citado, sem lógica de rollback efetiva encontrada.

- ✅ Task 5 — PPPoE + FreeRADIUS real
  - Conclusão: ❌ Não atendida
  - Observação: Nenhuma integração RADIUS claramente implementada no código.

- ✅ Task 6 — Billing com PIX
  - Conclusão: ❌ Não atendida
  - Observação: Sem integração com gateway de pagamentos detectada.

- ⚠️ Task 7 — Segurança RBAC
  - Conclusão: ⚠️ Parcialmente atendida
  - Observação: JWT mencionado; base para autenticação existe, mas papéis/roles e enforcement completos não foram verificados.

- ⚠️ Task 8 — Multi-tenant enforcement
  - Conclusão: ⚠️ Parcialmente atendida
  - Observação: TenantContext/documentação presente; falta evidência de filtros/enforcement em todos os pontos do código.

- ❌ Task 9 — Auditoria de ações críticas
  - Conclusão: ❌ Não atendida
  - Observação: Sem AuditLog ou mecanismo de logs imutáveis detectado.

- ❌ Task 10 — Testes de campo / E2E
  - Conclusão: ❌ Não atendida
  - Observação: Não foram encontrados testes E2E ou integração prática no repositório.

- ⚠️ Task 11 — Infra robusta (production readiness)
  - Conclusão: ⚠️ Parcialmente atendida
  - Observação: Docker/Docker Compose presentes; faltam exemplos de HTTPS, CI/CD, gestão de secrets e config de produção.

---

Próximos passos sugeridos:
1. Priorizar implementação das tasks críticas (1–6, 9–10) no repositório com PRs separadas.
2. Para RBAC e multi-tenant: adicionar testes de integração e um documento de enforcement de segurança.
3. Para infra: incluir exemplos básicos de CI (GH Actions), HTTPS e gestão de segredos.

Se desejar, eu posso abrir PRs de atualização (com mudanças de README e/ou scaffolding de código) ou criar issues detalhando as tarefas ausentes.
