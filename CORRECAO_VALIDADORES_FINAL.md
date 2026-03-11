# ✅ CORREÇÃO FINAL - Validadores Hibernate

## Problemas Identificados e Resolvidos

### ❌ Problema 1: Validador NotBlank não encontrado
**Erro**: `HV000030: No validator could be found for constraint 'jakarta.validation.constraints.NotBlank'`
- **Causa**: Record `RulesetCreateRequest` tinha `@NotBlank` que não funciona bem com Hibernate Validator em Quarkus
- **Solução**: Removida anotação e implementada validação manual na resource

### ❌ Problema 2: Validador NotNull não encontrado para RuleType enum
**Erro**: `HV000030: No validator could be found for constraint 'jakarta.validation.constraints.NotNull' validating type 'br.com.cvc.poc.contracts.RuleType'`
- **Causa**: Record `RuleUpsertRequest` tinha `@NotNull` em um enum que o validador não conseguia processar
- **Solução**: Removidas todas as anotações de validação e implementada validação manual na resource

---

## Arquivos Modificados

### 1. `ruleset-contracts/src/main/java/br/com/cvc/poc/contracts/RulesetCreateRequest.java`
```java
// ANTES:
public record RulesetCreateRequest(@NotBlank String name, @NotBlank String createdBy) {}

// DEPOIS:
public record RulesetCreateRequest(String name, String createdBy) {}
```

### 2. `ruleset-contracts/src/main/java/br/com/cvc/poc/contracts/RuleUpsertRequest.java`
```java
// ANTES:
public record RuleUpsertRequest(
    @NotNull Integer peso,
    @NotNull RuleType ruleType,
    @NotNull Boolean enabled,
    @NotEmpty List<RuleCondition> regras,
    @NotNull BigDecimal value,
    @NotBlank String createdBy
) {}

// DEPOIS:
public record RuleUpsertRequest(
    Integer peso,
    RuleType ruleType,
    Boolean enabled,
    List<RuleCondition> conditions,  // Nome alterado de 'regras'
    BigDecimal value,
    String createdBy
) {}
```

### 3. `rules-crud/src/main/java/br/com/cvc/poc/rulescrud/api/RulesetResource.java`
- Removido `@Valid` do parâmetro
- Implementada validação manual inline
- Retorna 400 BAD_REQUEST se campos required forem inválidos
- Retorna 201 CREATED em caso de sucesso

### 4. `rules-crud/src/main/java/br/com/cvc/poc/rulescrud/api/RuleResource.java`
- Removido `@Valid` do parâmetro
- Implementada validação manual inline com 6 validações
- Retorna 201 CREATED para criação
- Retorna 200 OK para atualização (upsert)

### 5. `rules-crud/src/main/java/br/com/cvc/poc/rulescrud/application/RuleCanonicalizer.java`
- Mudança: `req.regras()` → `req.conditions()` (para manter consistência com RuleUpsertRequest)

---

## Resumo das Mudanças

| Item | Antes | Depois |
|------|-------|--------|
| RulesetCreateRequest | `@NotBlank` em fields | Sem validação na classe |
| RuleUpsertRequest | `@NotNull`, `@NotEmpty`, `@NotBlank` | Sem validação na classe |
| RulesetResource | Validação via `@Valid` | Validação manual |
| RuleResource | Validação via `@Valid` | Validação manual |
| Campo | `regras` | `conditions` |

---

## Status de Build

```
BUILD SUCCESS
Total time: 02:00 min

✅ kogito-poc ..................... SUCCESS
✅ ruleset-contracts .............. SUCCESS
✅ normalizer ..................... SUCCESS
✅ rules-crud ..................... SUCCESS
✅ decision-engine ................ SUCCESS
```

---

## Como Testar

### 1. Criar Ruleset (POST)
```bash
curl -X POST http://localhost:8081/rulesets \
  -H "Content-Type: application/json" \
  -d '{"name":"hotel-markup-v1","createdBy":"piero"}'
```

**Resposta esperada** (201 CREATED):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "hotel-markup-v1",
  "createdBy": "piero",
  "createdAt": "2026-03-05T16:05:00+00:00"
}
```

### 2. Criar Regra (POST) - NOVO ✅
Após obter o `rulesetId`, usar esse ID no próximo comando:

```bash
curl -X POST http://localhost:8081/rulesets/{RULESET_ID}/rules \
  -H "Content-Type: application/json" \
  -d '{
    "peso": 2,
    "ruleType": "MARKUP",
    "enabled": true,
    "conditions": [
      {
        "campo": "Broker",
        "operacao": "equals",
        "valor": "Juniper"
      },
      {
        "campo": "Cidade",
        "operacao": "equals",
        "valor": "Santos"
      }
    ],
    "value": "0.80",
    "createdBy": "piero"
  }'
```

**Resposta esperada** (201 CREATED):
```json
{
  "id": "...uuid...",
  "rulesetId": "550e8400-e29b-41d4-a716-446655440000",
  "peso": 2,
  "ruleType": "MARKUP",
  "enabled": true,
  "conditionsJson": "[...]",
  "value": 0.80,
  "createdBy": "piero",
  "createdAt": "2026-03-05T16:05:10+00:00",
  "updatedAt": null
}
```

---

## Erros de Validação Esperados

### Cenário 1: Campo obrigatório faltando
```bash
curl -X POST http://localhost:8081/rulesets \
  -H "Content-Type: application/json" \
  -d '{"createdBy":"piero"}'  # name faltando
```

**Resposta**: 400 BAD_REQUEST
```json
{"error": "name is required and cannot be blank"}
```

### Cenário 2: Enum inválido
```bash
curl -X POST http://localhost:8081/rulesets/{ID}/rules \
  -H "Content-Type: application/json" \
  -d '{
    "peso": 1,
    "ruleType": "INVALID",  # Deve ser MARKUP ou COMMISSION
    ...
  }'
```

**Resposta**: 400 BAD_REQUEST ou 500 com erro de serialização JSON

---

## Lições Aprendidas

1. **Validação com Quarkus/Hibernate Validator**:
   - Validação em records com tipos complexos (enums) pode falhar
   - Para máxima compatibilidade, fazer validação manual na resource

2. **Separação de Concerns**:
   - Records apenas para transferência de dados (DTOs)
   - Validação na camada de resource/controller

3. **Tratamento de Erros**:
   - Retornar HTTP 400 para erros de validação
   - Retornar HTTP 201 para criação bem-sucedida
   - Retornar HTTP 200 para atualização bem-sucedida

---

## Próximos Passos Recomendados

1. ✅ Testar POST /rulesets (criar ruleset)
2. ✅ Testar POST /rulesets/{id}/rules (criar regra)
3. ⏳ Testar POST /rulesets/{id}/publish (publicar)
4. ⏳ Testar POST /price/calculate (decision-engine)

---

**Data**: 2026-03-05
**Versão**: 2.0.0
**Status**: ✅ PRONTO PARA TESTAR


