# POC — Dynamic Rule Pricing Engine

## Overview
POC de motor de regras dinâmico para precificação com:
- Cadastro de regras com **peso único** (1 regra por peso, por ruleset e por ruleType)
- Campos **dinâmicos** (qualquer campo do inventário)
- Eleição determinística por ordem de peso
- Publish via Kafka + hot reload (atomic swap)
- Chunking/paralelismo + auditoria + benchmark

---

## Arquitetura

Serviços:
- `rules-crud` (porta **8081**): CRUD, validação/normalização, versionamento, publish Kafka
- `decision-engine` (porta **8082**): consume Kafka, atomic swap, avaliação, chunking, auditoria

### Fluxos

**rules-crud**
```text
Create/Update Rule -> Normalize -> Persist -> Publish (Kafka)
````

**decision-engine**

```text
Consume (Kafka) -> Build Runtime -> Atomic Swap -> Evaluate Requests
```

---

## Estratégia de eleição (peso único)

Para cada `ruleType` (MARKUP/COMMISSION), o engine avalia regras em **ordem crescente de peso**.

Regras:

1. Se a regra possui **campo que não existe** na pesquisa → **skip** (ignora a regra)
2. Se todos os campos existem, mas **alguma condição falha** → próxima regra
3. Se **todas as condições passam** → aplica `value`
4. Se nenhuma regra casar → retorna `0`

Pseudo-código:

```text
for rule in rulesOrderedByWeight:
  if rule has field not present in search:
    continue
  if all conditions match:
    return rule.value
return 0
```

---

## Estrutura da regra (exemplo)

```json
{
  "peso": 2,
  "ruleType": "MARKUP",
  "enabled": true,
  "conditions": [
    { "campo": "Broker",   "operacao": "equals", "valor": "Juniper" },
    { "campo": "Cidade",   "operacao": "equals", "valor": "Santos" },
    { "campo": "Checkout", "operacao": "lte",    "valor": "26/10/2026" }
  ],
  "value": "0.80",
  "createdBy": "piero"
}
```

---

## Operadores suportados

* `equals`
* `not_equals`
* `lt`, `lte`, `gt`, `gte`
* `in`

---

## Normalização de campos

O nome dos campos é normalizado **no cadastro da regra** e **na pesquisa**:

* lower-case
* remove acentos/diacríticos
* trim
* normaliza separadores (quando aplicável)

Exemplos:

* `CaféDaManha` → `cafedamanha`
* `Check-in` → `check_in`
* `hotelId` → `hotelid`

---

## Datas

* Formato: `dd/MM/yyyy`
* Comparação por `LocalDate`
* Se o valor da data na regra for vazio (`null`/`""`) → **ignora a condição** (assume match)

---

## Pax

* `QntdePax` (ou equivalente do inventário) é **obrigatório na pesquisa**

---

## Estrutura do repo

```text
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

Guarde o `rulesetId` retornado.

---

## Criar Regra

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

## Publish (Kafka)

```bash
curl -X POST http://localhost:8081/rulesets/{RULESET_ID}/publish \
  -H "Content-Type: application/json" \
  -d '{"publishedBy":"piero"}'
```

O `decision-engine` fará hot reload e atomic swap automaticamente.

---

## Precificar (sync)

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

## Auditoria

A resposta inclui auditoria por item com:

* regra aplicada (ruleId/peso)
* trilha de fallback (pesos tentados)
* motivo de skip (campo ausente), quando aplicável

---

## Benchmark

Gerar massa:

```bash
python3 scripts/gen_data.py --items 100000 --out /tmp/items_100k.json
```

Rodar:

```bash
./scripts/bench.sh <RULESET_ID>
```

---

## Observabilidade

Métricas:

* `http://localhost:8082/q/metrics`

Principais:

* `reload_success_total`
* `reload_fail_total`
* `compile_time_ms`
* `items_processed_total`
* `items_per_second`

```

---
