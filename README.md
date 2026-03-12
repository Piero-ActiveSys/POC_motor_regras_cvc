# POC — Dynamic Rule Pricing Engine

## Overview

Esta POC implementa um **motor de regras dinâmico para precificação**, capaz de aplicar **regras configuráveis por prioridade (peso)** para calcular **markup e comissão**.

O objetivo da POC é validar:

- cadastro dinâmico de regras
- avaliação determinística por prioridade
- atualização dinâmica das regras
- performance em grandes volumes de requisições
- hot reload via Kafka

---

## Arquitetura

A solução possui **dois serviços principais**:

- `rules-crud`
- `decision-engine`

---

### rules-crud

Responsável por:

- CRUD de regras
- validação
- normalização
- versionamento
- geração do modelo canônico
- publicação de eventos Kafka

Fluxo:

```

Create Rule
↓
Normalize
↓
Persist
↓
Publish Event (Kafka)

```

---

### decision-engine

Responsável por:

- consumir eventos Kafka
- hot reload de regras
- avaliação das regras
- cálculo de markup / comissão
- processamento paralelo (chunking)

Fluxo:

```

Kafka Event
↓
Build Runtime
↓
Atomic Swap
↓
Evaluation Engine

````

---

## Estratégia de eleição de regras

Cada regra possui um **peso único** (1 regra por peso).

Exemplo:

| Peso | Regra |
|------|------|
| 0 | mais específica |
| 1 | específica |
| 2 | genérica |
| 3 | fallback |

---

### Algoritmo

Para cada `RuleType` (MARKUP/COMMISSION), o engine avalia as regras **em ordem crescente de peso**:

- Se a regra possui um campo que **não existe na pesquisa** → **ignora a regra** e passa para o próximo peso
- Se todos os campos existem, mas **alguma condição falha** → passa para o próximo peso
- Se **todas as condições passam** → retorna o `value` da regra
- Se nenhuma regra fizer match → retorna `0`

Pseudo-código:

```text
for rule in rulesOrderedByWeight:

    if rule contains field not present in search:
        continue

    if all conditions match:
        return rule.value

return 0
````

---

## Estrutura de regra

Exemplo:

```json
{
  "peso": 0,
  "ruleType": "MARKUP",
  "conditions": [
    { "campo": "Broker", "operacao": "equals", "valor": "Juniper" },
    { "campo": "Cidade", "operacao": "equals", "valor": "Santos" }
  ],
  "value": 0.98
}
```

---

## Operadores suportados

| Operador     | Descrição        |
| ------------ | ---------------- |
| `equals`     | igualdade        |
| `not_equals` | diferente        |
| `lt`         | menor que        |
| `lte`        | menor ou igual   |
| `gt`         | maior que        |
| `gte`        | maior ou igual   |
| `in`         | lista de valores |

> Observação: Operadores numéricos e de data usam comparação por tipo (ex.: `LocalDate` para datas).

---

## Normalização de campos

Tanto no **cadastro** quanto na **pesquisa**, os nomes dos campos são normalizados para evitar inconsistências do inventário (case/acentos/separadores).

Transformações aplicadas:

* lowercase
* remoção de acentos/diacríticos
* troca de separadores para `_` quando aplicável
* trim

Exemplos:

* `CaféDaManha` → `cafedamanha`
* `Check-in` → `check_in`
* `hotelId` → `hotelid`

---

## Datas

* Formato esperado: `dd/MM/yyyy`
* Comparação por `LocalDate`

### Regras com data vazia

Se a regra tiver `Checkin` vazio e/ou `Checkout` vazio, **a condição é ignorada e assume match** (não bloqueia a regra).

---

## Pax

`QntdePax` (ou equivalente do inventário) é **obrigatório na pesquisa**.

---

## Estrutura do projeto

```
monorepo/
  docker-compose.yml
  README.md
  scripts/
    bench.sh
    gen_data.py
  ruleset-contracts/
  normalizer/
  rules-crud/
  decision-engine/
```

---

## Pré-requisitos

* Java 21
* Maven
* Docker
* Python 3

---

## Build inicial (monorepo)

Antes de subir os serviços, compile os módulos compartilhados:

```bash
mvn clean install
```

---

## Subir infraestrutura (Kafka + Postgres)

```bash
docker compose up -d
```

---

## Subir serviços

Terminal 1:

```bash
cd rules-crud
mvn quarkus:dev
```

Terminal 2:

```bash
cd decision-engine
mvn quarkus:dev
```

---

## Criar Ruleset

```bash
curl -X POST http://localhost:8081/rulesets \
  -H "Content-Type: application/json" \
  -d '{"name":"hotel-markup-v1","createdBy":"piero"}'
```

A resposta retorna o `rulesetId` para ser usado nos próximos passos.

---

## Criar regra (exemplo)

```bash
curl -X POST http://localhost:8081/rulesets/{RULESET_ID}/rules \
  -H "Content-Type: application/json" \
  -d '{
    "peso": 2,
    "ruleType":"MARKUP",
    "enabled":true,
    "conditions":[
      { "campo":"Broker", "operacao":"equals", "valor":"Juniper" },
      { "campo":"Cidade", "operacao":"equals", "valor":"Santos" },
      { "campo":"Checkout", "operacao":"lte", "valor":"26/10/2026" }
    ],
    "value":"0.80",
    "createdBy":"piero"
  }'
```

---

## Publish (gera runtime + evento Kafka)

```bash
curl -X POST http://localhost:8081/rulesets/{RULESET_ID}/publish \
  -H "Content-Type: application/json" \
  -d '{"publishedBy":"piero"}'
```

O `decision-engine` irá consumir o evento, compilar o runtime e fazer **atomic swap**.

---

## Precificação (sync)

```bash
curl -X POST http://localhost:8082/price/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "requestId":"req-001",
    "rulesetId":"{RULESET_ID}",
    "items":[
      {
        "itemId":"i-1",
        "fields":{
          "Broker":"Juniper",
          "Cidade":"Santos",
          "Estado":"São Paulo",
          "Checkin":"20/10/2026",
          "Checkout":"25/10/2026",
          "QntdePax":5
        }
      }
    ]
  }'
```

---

## Auditoria (por item)

A resposta inclui auditoria por item contendo:

* regra aplicada (peso, ruleId)
* trilha de fallback (pesos tentados)
* motivo de skip (campo ausente) quando aplicável

---

## Benchmark

### Gerar massa de dados

vide readme.md dentro da pasta Script

---

## Observabilidade

Métricas em:

* `http://localhost:8082/q/metrics`

Principais métricas:

* `reload_success_total`
* `reload_fail_total`
* `compile_time_ms`
* `items_processed_total`
* `items_per_second`

---

## Performance esperada

Com aproximadamente **5000 regras**:

* complexidade de busca: `O(n)` (sequencial por peso)
* com chunking + paralelismo: throughput elevado em batches

---

## Próximos passos

* UI para cadastro de regras
* catálogo de campos do inventário por broker
* validação de typo/field inexistente no CRUD
* indexação dinâmica (seleção automática de campos mais discriminantes)
