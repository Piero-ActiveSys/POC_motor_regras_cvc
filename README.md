# POC — Dynamic Rule Pricing Engine

## Overview

Esta POC implementa um **motor de regras dinâmico para precificação**, capaz de aplicar **regras configuráveis por prioridade (peso)** para calcular **markup e comissão**.

O objetivo da POC é validar:

- cadastro dinâmico de regras
- avaliação determinística por prioridade
- atualização dinâmica das regras
- performance em grandes volumes de requisições
- hot reload via Kafka
- **ativação por manifesto (sem payload pesado no Kafka)**
- **swap atômico de versão**
- **rollback explícito para versão anterior**

---

## Arquitetura

A solução possui **dois serviços principais**:

- `rules-crud`
- `decision-engine`

E **dois módulos compartilhados**:

- `ruleset-contracts` — contratos, DTOs e interfaces compartilhadas
- `normalizer` — normalização de texto, datas e coerção de valores

---

### rules-crud

Responsável por:

- CRUD de regras
- validação
- normalização (configurável via flag)
- versionamento
- geração de artefatos versionados em storage local
  - `ruleset_v{N}.drl`
  - `ruleset_v{N}.canonical.json`
  - `ruleset_v{N}.runtime.json`
  - `ruleset_v{N}.manifest.json`
- publicação de evento Kafka **leve** (apenas metadados)
- endpoint de rollback/reativação de versão

Fluxo:

```
Create Rule
↓
Normalize (if enabled)
↓
Persist
↓
/publish
↓
Generate artifacts (DRL, canonical, runtime, manifest)
↓
Write to storage
↓
Compute checksum
↓
Publish lightweight Kafka event (rulesetId, version, checksum, manifestPath)
```

---

### decision-engine

Responsável por:

- consumir evento Kafka leve
- localizar e validar manifesto no storage compartilhado
- carregar runtime.json pré-processado
- validar integridade (checksum)
- montar snapshot completo em memória
- trocar versão ativa de forma **atômica** (stage → validate → swap)
- compilação de DRL **opcional** (flag configurável)
- lock por rulesetId para concorrência
- política de versão e idempotência
- rollback via evento de reativação

Fluxo:

```
Kafka Event (lightweight)
↓
Lock per rulesetId
↓
Version acceptance policy check
↓
Idempotency check
↓
STAGE: Load manifest → Load runtime.json → (optional) Load DRL
↓
VALIDATE: Checksum verification → Parse runtime
↓
SWAP: Build RuntimeSnapshot → Atomic reference swap
```

---

## Artefatos de publicação

Cada publicação gera os seguintes arquivos em:
`{storage-base-dir}/{rulesetId}/v{version}/`

| Arquivo | Descrição |
|---------|-----------|
| `ruleset_v{N}.drl` | Regras Drools compiláveis |
| `ruleset_v{N}.canonical.json` | Representação canônica das regras |
| `ruleset_v{N}.runtime.json` | Runtime pré-processado (regras separadas por tipo, condições compiladas, índices) |
| `ruleset_v{N}.manifest.json` | Manifesto da publicação (ponto de entrada para ativação) |

### Exemplo de manifest.json

```json
{
  "rulesetId": "uuid",
  "version": 7,
  "eventType": "RULESET_PUBLISHED",
  "generatedAt": "2026-03-11T15:00:00Z",
  "normalizationApplied": true,
  "checksum": "sha256:...",
  "files": {
    "drl": "ruleset_v7.drl",
    "canonical": "ruleset_v7.canonical.json",
    "runtime": "ruleset_v7.runtime.json"
  }
}
```

---

## Evento Kafka (novo formato leve)

```json
{
  "schemaVersion": 1,
  "eventType": "RULESET_PUBLISHED",
  "rulesetId": "uuid",
  "version": 7,
  "checksum": "sha256:...",
  "publishedAt": "2026-03-11T15:00:00Z",
  "manifestPath": "uuid/v7/ruleset_v7.manifest.json"
}
```

Tipos de evento suportados:
- `RULESET_PUBLISHED` — nova versão publicada
- `RULESET_VERSION_ACTIVATED` — reativação de versão existente (rollback)

---

## Estratégia de eleição de regras

Cada regra possui um **peso único** (1 regra por peso).

| Peso | Regra |
|------|------|
| 0 | mais específica |
| 1 | específica |
| 2 | genérica |
| 3 | fallback |

### Algoritmo

Para cada `RuleType` (MARKUP/COMMISSION), o engine avalia as regras **em ordem crescente de peso**:

- Se a regra possui um campo que **não existe na pesquisa** → **ignora a regra**
- Se todos os campos existem, mas **alguma condição falha** → passa para o próximo peso
- Se **todas as condições passam** → retorna o `value` da regra
- Se nenhuma regra fizer match → retorna `0`

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

---

## Normalização de campos

Configurável via `rules.normalization.enabled` (default: `true`).

Transformações aplicadas:
* lowercase
* remoção de acentos/diacríticos
* troca de separadores para `_`
* trim

Exemplos: `CaféDaManha` → `cafedamanha`, `Check-in` → `check_in`

---

## Configurações

### rules-crud (`application.yml`)

| Propriedade | Default | Descrição |
|-------------|---------|-----------|
| `ruleset.storage.base-dir` | `./storage` | Diretório base dos artefatos |
| `rules.normalization.enabled` | `true` | Habilita normalização no cadastro |

### decision-engine (`application.yml`)

| Propriedade | Default | Descrição |
|-------------|---------|-----------|
| `ruleset.storage.base-dir` | `./storage` | Diretório compartilhado de artefatos |
| `engine.drl.compile-on-activation` | `false` | Compila DRL na ativação |
| `engine.index.fields` | `Broker,Cidade,Estado,hotelId` | Campos preferenciais para indexação |
| `engine.chunkSize` | `10000` | Tamanho do chunk para processamento paralelo |
| `engine.parallelism` | `0` (auto) | Nível de paralelismo |

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

---

## Criar regra

```bash
curl -X POST http://localhost:8081/rulesets/{RULESET_ID}/rules \
  -H "Content-Type: application/json" \
  -d '{
    "peso": 2,
    "ruleType":"MARKUP",
    "enabled":true,
    "regras":[
      { "campo":"Broker", "operacao":"equals", "valor":"Juniper" },
      { "campo":"Cidade", "operacao":"equals", "valor":"Santos" }
    ],
    "value":"0.80",
    "createdBy":"piero"
  }'
```

---

## Publish

```bash
curl -X POST http://localhost:8081/rulesets/{RULESET_ID}/publish \
  -H "Content-Type: application/json" \
  -d '{"publishedBy":"piero"}'
```

Resposta (lean — sem DRL nem canonical JSON):
```json
{
  "rulesetId": "uuid",
  "version": 1,
  "eventType": "RULESET_PUBLISHED",
  "publishedAt": "2026-03-12T...",
  "checksum": "sha256:...",
  "manifestPath": "uuid/v1/ruleset_v1.manifest.json"
}
```

---

## Rollback / Ativar versão anterior

```bash
# Ativar versão específica
curl -X POST http://localhost:8081/rulesets/{RULESET_ID}/activate-version/1?requestedBy=piero

# Rollback para versão anterior (current - 1)
curl -X POST http://localhost:8081/rulesets/{RULESET_ID}/rollback?requestedBy=piero
```

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
        "qntdePax": 5,
        "Broker":"Juniper",
        "Cidade":"Santos",
        "Estado":"São Paulo",
        "Checkin":"20/10/2026",
        "Checkout":"25/10/2026"
      }
    ]
  }'
```

---

## Auditoria

A resposta inclui auditoria por item contendo:
* regra aplicada (peso, ruleId)
* trilha de fallback (pesos tentados)
* motivo de skip

---

## Observabilidade

Métricas em:
* `http://localhost:8082/q/metrics`

---

## Runbook Operacional

### Como identificar a versão ativa

O `decision-engine` loga a cada swap:
```
Swap completed: ruleset {id} v{old} -> v{new} (checksum=...)
```

### Como publicar nova versão

1. Crie/atualize regras via API
2. Chame `POST /rulesets/{id}/publish`
3. Verifique os logs do rules-crud para confirmação de artefatos
4. Verifique os logs do decision-engine para confirmação de ativação

### Como reativar versão anterior (rollback)

```bash
POST /rulesets/{id}/activate-version/{version}
```

Ou para voltar uma versão:
```bash
POST /rulesets/{id}/rollback
```

### Como validar integridade dos arquivos

O checksum SHA-256 é calculado sobre `drl|canonical|runtime`. Ele é salvo no manifesto e no evento Kafka. O decision-engine valida o checksum antes do swap.

### Como diagnosticar falha de ativação

Verifique os logs do decision-engine:
- `Manifest not found` → arquivo não existe no storage
- `Checksum mismatch` → artefatos corrompidos ou inconsistentes
- `Runtime file not found` → runtime.json ausente
- `Unsupported schema version` → evento de versão incompatível

### Logs e métricas para consultar

- Logs do rules-crud: publicação, geração de artefatos, checksum
- Logs do decision-engine: evento recebido, stage/validate/swap, versão anterior/nova
- Métricas Prometheus: `http://localhost:8082/q/metrics`

---

## Estrutura do projeto

```
monorepo/
  docker-compose.yml
  README.md
  scripts/
  ruleset-contracts/    # Contratos, DTOs, interfaces
  normalizer/           # Normalização de texto/datas
  rules-crud/           # CRUD + publicação + storage
  decision-engine/      # Consumo + ativação + pricing
```
