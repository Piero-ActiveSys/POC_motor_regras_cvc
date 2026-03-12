# README — scripts de geração e carga em lote

Esta pasta contém os scripts para:

- gerar massa de regras de `MARKUP` e `COMMISSION`
- gerar payload de consulta compatível com `/price/calculate`
- cadastrar regras no `rules-crud`
- cadastrar regras em lote no novo endpoint `/rulesets/{rulesetId}/rules/batch`
- executar benchmark simples de throughput da carga em lote

## Arquivos

```text
scripts/
├── bench.sh
├── gen_data.py
├── generated_queries.json
└── generated_rules.json
```

## O que mudou

Antes o script executava 1 `POST /rules` por regra.

Agora o fluxo principal de alta performance usa:

```text
POST /rulesets/{rulesetId}/rules/batch
```

com vários registros por requisição.

O endpoint legado continua existindo:

```text
POST /rulesets/{rulesetId}/rules
```

Então:

- contratos atuais foram mantidos
- foi adicionado o contrato em lote
- o `gen_data.py` continua aceitando os parâmetros antigos
- foram adicionados novos parâmetros opcionais para batch

---

## Endpoint novo em lote

### URL

```text
POST /rulesets/{rulesetId}/rules/batch
```

### Body

```json
{
  "rules": [
    {
      "peso": 1,
      "ruleType": "MARKUP",
      "enabled": true,
      "regras": [
        { "campo": "Broker", "operacao": "equals", "valor": "Omnibees" },
        { "campo": "Cidade", "operacao": "equals", "valor": "Santos" }
      ],
      "value": "10.0",
      "createdBy": "piero"
    }
  ],
  "chunkSize": 1000
}
```

### Resposta esperada

```json
{
  "rulesetId": "UUID",
  "requestedCount": 1000,
  "processedCount": 1000,
  "chunkSize": 1000,
  "elapsedMs": 120
}
```

---

## Pré-requisitos

### Python

Necessário Python 3.9+.

No Windows, para validar:

```powershell
python --version
```

ou

```powershell
py --version
```

### Serviço rules-crud

O serviço precisa estar no ar, por padrão em:

```text
http://localhost:8081
```

---

## Passo a passo

### 1) Criar um ruleset

Exemplo:

```bash
curl --location 'http://localhost:8081/rulesets' \
  --header 'Content-Type: application/json' \
  --data '{
    "name": "bench-ruleset",
    "createdBy": "piero"
  }'
```

Guarde o `id` retornado.

---

### 2) Gerar arquivos localmente sem enviar para a API

```bash
python gen_data.py \
  --ruleset-id SEU_RULESET_ID \
  --count 1000
```

Arquivos gerados:

```text
generated_rules.json
generated_queries.json
```

---

### 3) Gerar e enviar em lote para a API

```bash
python gen_data.py \
  --ruleset-id SEU_RULESET_ID \
  --count 30000 \
  --execute \
  --batch-size 1000 \
  --parallelism 4
```

Esse é o modo recomendado para volume alto.

---

### 4) Gerar, enviar em lote e publicar o ruleset

```bash
python gen_data.py \
  --ruleset-id SEU_RULESET_ID \
  --count 30000 \
  --execute \
  --publish \
  --batch-size 1000 \
  --parallelism 4
```

---

### 5) Forçar o comportamento antigo, 1 regra por request

```bash
python gen_data.py \
  --ruleset-id SEU_RULESET_ID \
  --count 1000 \
  --execute \
  --single-endpoint
```

Use apenas para comparação ou compatibilidade.

---

### 6) Rodar benchmark simples de carga em lote

```bash
./bench.sh SEU_RULESET_ID 30000 1000 4
```

Parâmetros do `bench.sh`:

```text
./bench.sh <rulesetId> [count] [batchSize] [parallelism]
```

Exemplo:

```bash
./bench.sh 6a1de01b-3a2d-431f-8cf3-5b2cb59b4720 30000 1000 4
```

---

## Parâmetros do gen_data.py

### Parâmetros existentes

| Parâmetro | Descrição |
|---|---|
| `--ruleset-id` | UUID do ruleset já criado |
| `--count` | quantidade total de regras |
| `--created-by` | usuário criador da regra |
| `--crud-base-url` | base URL do CRUD |
| `--calc-base-url` | base URL do calc, mantido por compatibilidade |
| `--seed` | seed para geração determinística |
| `--execute` | envia as regras para a API |
| `--publish` | publica o ruleset após o cadastro |
| `--out-rules` | arquivo de saída das regras geradas |
| `--out-queries` | arquivo de saída do payload de consulta |
| `--request-id` | requestId do payload de consulta |

### Novos parâmetros

| Parâmetro | Descrição |
|---|---|
| `--batch-size` | quantidade de regras por requisição ao endpoint em lote |
| `--parallelism` | quantidade de lotes enviados em paralelo |
| `--single-endpoint` | força o uso do endpoint legado `POST /rules` |
| `--chunk-size` | chunk interno informado para o endpoint `/batch` |

---

## Distribuição das regras

Se for solicitado `N` regras, o script divide automaticamente:

| Tipo | Quantidade |
|---|---:|
| MARKUP | `N / 2` |
| COMMISSION | restante |

Exemplo:

```text
--count 1000
```

Resultado:

```text
500 MARKUP
500 COMMISSION
```

---

## Operadores utilizados

O script gera regras contemplando os operadores suportados pelo motor:

```text
equals
not_equals
lt
lte
gt
gte
in
```

---

## Campos utilizados

```text
Broker
Cidade
Estado
Checkin
Checkout
qntdePax
Refundable
CafeDaManha
RoomType
```

---

## Observações de performance

Para volume alto:

- prefira `--execute` sem `--single-endpoint`
- comece com `--batch-size 1000`
- teste `--parallelism 2`, `4` e `8`
- publique apenas ao final
- use o `bench.sh` para medir throughput de forma rápida

Configuração inicial recomendada:

```bash
python gen_data.py \
  --ruleset-id SEU_RULESET_ID \
  --count 30000 \
  --execute \
  --batch-size 1000 \
  --parallelism 4
```

---

## Saídas geradas

### generated_rules.json

Contém as regras geradas.

### generated_queries.json

Contém um payload compatível com o motor de cálculo:

```json
{
  "requestId": "bench-generated",
  "rulesetId": "RULESET_ID",
  "items": [
    {
      "itemId": "markup-1",
      "qntdePax": 3,
      "Broker": "Juniper",
      "Cidade": "Santos",
      "Estado": "São Paulo",
      "Checkin": "20/10/2026",
      "Checkout": "25/10/2026"
    }
  ]
}
```
