# Decision Engine v2 — Relatório Técnico de Arquitetura

> **Versão**: 2.0.0  
> **Data**: 13/03/2026  
> **Classificação**: Documento técnico para avaliação de arquitetura  

---

## Sumário

1. [Visão Geral](#1-visão-geral)
2. [Stack Tecnológico](#2-stack-tecnológico)
3. [Decomposição de Módulos](#3-decomposição-de-módulos)
4. [Diagrama de Componentes](#4-diagrama-de-componentes)
5. [Fluxo de Publicação de Regras](#5-fluxo-de-publicação-de-regras)
6. [Fluxo de Ativação no Decision-Engine](#6-fluxo-de-ativação-no-decision-engine)
7. [Fluxo de Avaliação de Pricing (Hot Path)](#7-fluxo-de-avaliação-de-pricing-hot-path)
8. [Modelo de Dados em Runtime](#8-modelo-de-dados-em-runtime)
9. [Patterns e Decisões de Arquitetura](#9-patterns-e-decisões-de-arquitetura)
10. [Inventário de Otimizações de Performance](#10-inventário-de-otimizações-de-performance)
11. [Resultados de Benchmark](#11-resultados-de-benchmark)
12. [Escalabilidade e Dimensionamento](#12-escalabilidade-e-dimensionamento)
13. [Observabilidade](#13-observabilidade)
14. [Riscos e Considerações](#14-riscos-e-considerações)
15. [Glossário](#15-glossário)

---

## 1. Visão Geral

O **Decision Engine** é um motor de regras dinâmico para precificação (pricing) em tempo real. Ele avalia regras de **markup** e **comissão** sobre itens de hospedagem, permitindo que regras sejam cadastradas, publicadas e ativadas **sem redeployment** da aplicação.

### Problema que resolve

Sistemas de pricing tradicionais codificam regras em código-fonte ou tabelas rígidas. Qualquer mudança exige release. Este motor permite que **regras sejam gerenciadas via API**, publicadas como artefatos versionados e ativadas no runtime via evento Kafka — com **zero downtime** e **rollback instantâneo**.

### Requisitos atendidos

| Requisito | Como é atendido |
|---|---|
| Alteração de regras sem redeployment | Publicação via API + ativação via Kafka |
| Latência sub-segundo para pricing | Engine in-memory com índice hash + cache por request |
| Consistência entre instâncias | Evento Kafka garante que todas as réplicas ativam a mesma versão |
| Auditoria e rastreabilidade | Artefatos versionados em storage + checksum SHA-256 |
| Rollback instantâneo | Reativação de versão anterior via `activate-version/{version}` |

---

## 2. Stack Tecnológico

### Linguagem e Runtime

| Componente | Tecnologia | Versão | Justificativa |
|---|---|---|---|
| Linguagem | Java | 21 (LTS) | Records, pattern matching, virtual threads |
| Framework | Quarkus | 3.15.2 | Startup rápido, CDI leve, integração nativa com Kafka e Prometheus |
| Build | Maven | 3.x | Multi-module, profiles para build nativo |
| JDK recomendado | Oracle GraalVM JDK | 21.0.10 | Graal JIT compiler: -8% em tail latency, 50% menos variância |

### Infraestrutura

| Componente | Tecnologia | Versão | Papel |
|---|---|---|---|
| Banco de dados | PostgreSQL | 16 | Persistência de rulesets, regras e versões (rules-crud) |
| Mensageria | Apache Kafka | 7.6.1 (Confluent) | Evento de publicação/ativação de rulesets |
| ORM / Migrations | Hibernate ORM Panache + Flyway | — | Acesso a dados + versionamento de schema |
| Métricas | Micrometer + Prometheus | — | Exposição de métricas de runtime |

### Bibliotecas-chave

| Biblioteca | Uso |
|---|---|
| Jackson | Serialização/deserialização JSON de contratos e artefatos |
| SmallRye Reactive Messaging | Consumer/producer Kafka reativo |
| Hibernate Validator | Validação de contratos de entrada via Bean Validation |
| Drools (9.44.0) | Bridge opcional para DRL — desativável via flag |

---

## 3. Decomposição de Módulos

```
kogito-poc-v2/
├── ruleset-contracts/     ← DTOs e contratos compartilhados (PricingRequest, RuleCondition, etc.)
├── normalizer/            ← Normalização de texto, datas, coerção de tipos
├── rules-crud/            ← CRUD de regras + publicação + storage de artefatos
├── decision-engine/       ← Motor de avaliação in-memory (hot path)
└── scripts/               ← Ferramentas de benchmark e geração de massa
```

### 3.1 ruleset-contracts (biblioteca)

Módulo **sem runtime**, apenas records Java imutáveis compartilhados entre rules-crud e decision-engine.

| Classe | Responsabilidade |
|---|---|
| `PricingRequest` / `PricingResponse` | Contrato da API `/price/calculate` |
| `PricingItem` | Item com campos dinâmicos via `@JsonAnySetter` |
| `RuleCondition` | Condição de regra: `(campo, operação, valor)` |
| `ConditionOp` | Enum de operadores: `equals`, `not_equals`, `lt`, `lte`, `gt`, `gte`, `in` |
| `RulesetEvent` | Evento Kafka leve (sem payload de regras — apenas `manifestPath`) |
| `ManifestDto` | Descreve o bundle de artefatos de uma versão publicada |
| `RuntimeDto` | Payload pré-processado pronto para consumo do engine |

### 3.2 normalizer (biblioteca)

Funções puras e stateless para normalização de valores antes da comparação.

| Classe | Responsabilidade |
|---|---|
| `TextNormalizer` | Lowercase, remoção de acentos, trim — para chaves e valores string |
| `DateNormalizer` | Parse de datas no formato `dd/MM/yyyy` → `LocalDate` |
| `ValueCoercion` | Conversão `Object → Double`, `Boolean`, `BigDecimal`, `List<String>` |

### 3.3 rules-crud (microsserviço — porta 8081)

Responsável pelo ciclo de vida das regras: CRUD, publicação, versionamento e emissão de eventos.

```
rules-crud/
├── api/
│   ├── RulesetResource      POST /rulesets
│   ├── RuleResource          POST /rulesets/{id}/rules, POST .../rules/batch
│   ├── PublishResource       POST /rulesets/{id}/publish
│   └── VersionResource       POST /rulesets/{id}/activate-version/{v}
├── application/
│   ├── PublishService        Orquestra publicação: gera artefatos, checksum, outbox
│   ├── RuntimeJsonBuilder    Gera runtime.json pré-compilado
│   ├── DrlBuilder            Gera DRL (Drools) a partir das regras
│   ├── Checksum              SHA-256 do bundle
│   └── RuleCanonicalizer     Normaliza regras antes da publicação
└── infra/
    ├── db/                   Entidades JPA (RulesetEntity, RuleEntity, OutboxEventEntity)
    ├── kafka/                OutboxPublisher (polling 1s)
    └── storage/              LocalFileArtifactStorage (filesystem)
```

**Padrão Outbox**: A publicação grava um `OutboxEventEntity` no banco (mesma transação que a versão). Um scheduler Quarkus faz polling a cada 1s e publica eventos pendentes no Kafka. Isso garante **atomicidade** entre a persistência e a emissão do evento.

### 3.4 decision-engine (microsserviço — porta 8082)

Motor de avaliação in-memory. Não acessa banco de dados em runtime.

```
decision-engine/
├── api/
│   └── PricingResource       POST /price/calculate, POST /price/jobs
├── application/
│   └── PricingService        Orquestra: preparar items → avaliar → merge resultado
├── runtime/
│   ├── RuntimeRegistry       Mapa atômico rulesetId → RuntimeSnapshot
│   ├── RuntimeSnapshot       Snapshot imutável de uma versão (índices + regras)
│   ├── RulesetRuntime        View de leitura do snapshot
│   ├── FieldTypeRegistry     Mapa campo → ValueKind (elimina exceptions no hot path)
│   ├── IndexBuilder          Constrói índice hash por campos preferenciais
│   ├── CompiledCondition     Condição pré-compilada e tipada (STRING, NUMBER, DATE, BOOLEAN)
│   ├── PreparedItem          Item com campos pré-parseados (normString, number, date, boolean)
│   ├── PreparedFieldValue    Valor de campo com múltiplas representações tipadas
│   ├── Evaluator             Busca no índice + matching + first-win por peso
│   ├── DynamicMatcher        Comparação tipada por campo (switch no ValueKind)
│   ├── MatchKeyBuilder       Gera chave de cache por item baseada nos campos ativos
│   └── DroolsCompiler        Compilação opcional de DRL
├── infra/
│   ├── kafka/                RulesetUpdatesConsumer (stage → validate → swap)
│   └── storage/              LocalFileArtifactReader (leitura de artefatos)
└── metrics/
    └── MetricsCollector      Coleta detalhada de tempos por fase (nanos)
```

---

## 4. Diagrama de Componentes

```
┌────────────────────────────────────────────────────────────────────────────┐
│                              INFRAESTRUTURA                                │
│  ┌──────────┐     ┌──────────────┐     ┌────────────────────────────────┐  │
│  │PostgreSQL│     │    Kafka     │     │   Filesystem (storage/)       │  │
│  │  :5432   │     │    :9092     │     │   ├── {rulesetId}/            │  │
│  │          │     │              │     │   │   ├── v1/                 │  │
│  │ rulesets │     │ topic:       │     │   │   │  ├── manifest.json   │  │
│  │ rules    │     │ ruleset_     │     │   │   │  ├── runtime.json    │  │
│  │ versions │     │ updates      │     │   │   │  ├── canonical.json  │  │
│  │ outbox   │     │              │     │   │   │  └── ruleset.drl     │  │
│  └────┬─────┘     └──────┬───────┘     └──────────┬─────────────────────┘  │
│       │                  │                         │                       │
│       │                  │                         │                       │
└───────┼──────────────────┼─────────────────────────┼───────────────────────┘
        │                  │                         │
   ┌────┴──────────┐  ┌───┴────────────────┐  ┌─────┴────────────────┐
   │  rules-crud   │  │  Kafka (bridge)    │  │  decision-engine    │
   │  :8081        │  │                    │  │  :8082              │
   │               │  │  OutboxPublisher   │  │                     │
   │ ┌───────────┐ │  │  (poll 1s)        │  │ ┌─────────────────┐ │
   │ │RuleResource│──┤  │  ──────────────▶ │──┤ │Consumer         │ │
   │ │PublishRes. │ │  │  RulesetEvent    │  │ │(stage→validate  │ │
   │ │VersionRes.│ │  │  {manifestPath}  │  │ │ →swap)          │ │
   │ └───────────┘ │  └──────────────────┘  │ └────────┬────────┘ │
   │               │                         │          │          │
   │ ┌───────────┐ │                         │ ┌────────▼────────┐ │
   │ │PublishSvc  │ │                         │ │RuntimeRegistry  │ │
   │ │ ┌────────┐│ │                         │ │ AtomicRef<      │ │
   │ │ │Checksum││ │                         │ │ RuntimeSnapshot>│ │
   │ │ │DRL     ││ │                         │ │                 │ │
   │ │ │Runtime ││ │                         │ │ ┌─────────────┐ │ │
   │ │ │JSON    ││ │                         │ │ │FieldTypeReg.│ │ │
   │ │ └────────┘│ │                         │ │ │IndexBuilder │ │ │
   │ └───────────┘ │                         │ │ │Compiled     │ │ │
   │               │                         │ │ │Conditions   │ │ │
   │  Writes ──────┼─────────────────────────┤ │ └─────────────┘ │ │
   │  artifacts    │                         │ │                 │ │
   │  to storage   │                         │ └────────┬────────┘ │
   │               │                         │          │          │
   └───────────────┘                         │ ┌────────▼────────┐ │
                                             │ │PricingResource  │ │
                                             │ │POST /calculate  │ │
                                             │ │                 │ │
                                             │ │PricingService   │ │
                                             │ │ ┌─────────────┐ │ │
                                             │ │ │Evaluator    │ │ │
                                             │ │ │DynamicMatch │ │ │
                                             │ │ │MatchKeyCache│ │ │
                                             │ │ └─────────────┘ │ │
                                             │ └─────────────────┘ │
                                             └─────────────────────┘
```

---

## 5. Fluxo de Publicação de Regras

```
Operador/Frontend
       │
       ▼
  POST /rulesets/{id}/publish
       │
       ▼
  PublishService.publish()
       │
       ├── 1. Fetch regras do PostgreSQL
       ├── 2. Canonicalizar (normalizar campos, ordenar por peso)
       ├── 3. Gerar DRL (Drools)
       ├── 4. Gerar runtime.json (pré-compilado com ValueKind, setValues, etc.)
       ├── 5. Calcular checksum SHA-256 do bundle
       ├── 6. Gravar artefatos no filesystem (storage/{rulesetId}/v{N}/)
       ├── 7. Gerar e gravar manifest.json
       ├── 8. Validar que todos os artefatos existem
       ├── 9. Persistir RulesetVersionEntity (banco)
       └──10. Criar OutboxEventEntity (mesma transação)
                │
                ▼
         OutboxPublisher (scheduler 1s)
                │
                ▼
         Kafka topic: ruleset_updates
         Payload: RulesetEvent { schemaVersion, eventType, rulesetId,
                                  version, checksum, manifestPath }
```

**Decisão arquitetural**: O evento Kafka **não carrega as regras** — apenas o `manifestPath`. O consumer resolve o bundle via storage compartilhado. Isso mantém o evento leve (~200 bytes) e permite bundles de qualquer tamanho.

---

## 6. Fluxo de Ativação no Decision-Engine

```
Kafka topic: ruleset_updates
         │
         ▼
  RulesetUpdatesConsumer.onMessage()
         │
         ├── Parse evento + lock por rulesetId
         │
         ├── STAGE ────────────────────────────────
         │   ├── Ler manifest.json do storage
         │   ├── Ler runtime.json do storage
         │   └── (Opcional) Ler DRL do storage
         │
         ├── VALIDATE ─────────────────────────────
         │   ├── Checksum do manifest == checksum do evento?
         │   ├── Versão > versão atual? (ou reativação explícita)
         │   └── Schema version suportada?
         │
         ├── BUILD ────────────────────────────────
         │   ├── Parse RuntimeDto → List<RuleRuntime>
         │   ├── Compilar CompiledConditions (tipagem de valores)
         │   ├── Construir FieldTypeRegistry (campo → ValueKind)
         │   ├── Construir IndexBuilder (índice hash por campos preferenciais)
         │   ├── Separar markupRules / commissionRules
         │   └── (Opcional) Compilar DRL via DroolsCompiler
         │
         └── SWAP (atômico) ───────────────────────
             └── registry.swap(rulesetId, newSnapshot)
                 │
                 └── AtomicReference.getAndSet() ← lock-free
```

**Decisão arquitetural**: O swap é **atômico via `AtomicReference`**. Requests em andamento continuam usando o snapshot anterior. Novos requests pegam o novo snapshot. Não há janela de inconsistência.

---

## 7. Fluxo de Avaliação de Pricing (Hot Path)

Este é o caminho crítico de performance — executado a cada request.

```
POST /price/calculate
  { requestId, rulesetId, items: [...] }
         │
         ▼
  PricingService.calculate()
         │
         ├── 1. Obter RulesetRuntime (lock-free read do AtomicReference)
         │
         ├── 2. Particionar items em chunks (default: 128/chunk)
         │      └── Se parallelism > 1: ExecutorService (threads ou virtual threads)
         │
         └── 3. Para cada chunk → evalChunk():
                │
                ├── Para cada item:
                │   │
                │   ├── 3a. PREPARE ─────────────────────────────────────
                │   │   ├── Normalizar chaves (TextNormalizer.normKey)
                │   │   ├── Consultar FieldTypeRegistry.kindFor(campo)
                │   │   ├── PreparedFieldValue.fromTyped(valor, hint)
                │   │   │   └── STRING? → só normValue (zero exceptions)
                │   │   │   └── NUMBER? → só parseNumber
                │   │   │   └── DATE?   → só parseDate
                │   │   │   └── BOOLEAN?→ só toBoolean
                │   │   └── Gerar IndexBuilder.keysForSearch() (chaves de busca)
                │   │
                │   ├── 3b. CACHE CHECK ─────────────────────────────────
                │   │   ├── Gerar MatchKeyBuilder.build(item, activeFields)
                │   │   │   └── Apenas campos que alguma regra usa → ignora hotelId, roomId, etc.
                │   │   └── Hit no cache? → pula avaliação (retorna resultado anterior)
                │   │
                │   ├── 3c. EVALUATE (markup + commission) ──────────────
                │   │   │
                │   │   ├── INDEX LOOKUP (O(1)) ─────────────────────────
                │   │   │   ├── Tentar cada key do item no HashMap do índice
                │   │   │   │   ex: "broker=omnibees|cidade=sao paulo" → bucket [r3, r7, r12]
                │   │   │   └── Merge bucket + catch-all (regras genéricas) em ordem de peso
                │   │   │
                │   │   └── MATCH (linear no bucket) ────────────────────
                │   │       ├── Para cada regra candidata (ordenada por peso):
                │   │       │   ├── DynamicMatcher.matchesAll(rule, item)
                │   │       │   │   └── Para cada CompiledCondition:
                │   │       │   │       ├── switch(kind):
                │   │       │   │       │   STRING  → compareTo(normalizedString)
                │   │       │   │       │   NUMBER  → compareTo(doubleValue)
                │   │       │   │       │   DATE    → compareTo(localDate)
                │   │       │   │       │   BOOLEAN → Objects.equals(boolean)
                │   │       │   │       │   SET     → Set.contains(string)
                │   │       │   │       └── switch(op): lt, lte, gt, gte, equals, not_equals, in
                │   │       │   │
                │   │       │   └── FIRST WIN → retorna AppliedDecision (ruleId, peso, value)
                │   │       │
                │   │       └── Nenhum match → AppliedDecision("NO_MATCH", 0)
                │   │
                │   └── 3d. MERGE resultado (markup + commission) → PricingItemResult
                │
                └── Retornar PricingResponse { results: [...] }
```

### Estratégia First-Win

As regras são avaliadas em **ordem crescente de peso** (peso menor = mais específica). A **primeira regra que faz match** vence. Isso é análogo ao modelo de "especificidade" do CSS — regras mais específicas têm peso menor e prioridade maior.

---

## 8. Modelo de Dados em Runtime

```
RuntimeRegistry
  └── ConcurrentHashMap<rulesetId, AtomicReference<RuntimeSnapshot>>

RuntimeSnapshot (imutável)
  ├── rulesetId, version, checksum, loadedAt
  ├── preferredIndexFields: ["broker", "cidade", "estado", ...]
  ├── fieldTypeRegistry: FieldTypeRegistry
  │   └── Map<"broker" → STRING, "checkout" → DATE, "cafedamanha" → BOOLEAN, ...>
  ├── markupIndex: Map<"broker=omnibees|cidade=santos" → [RuleRuntime, ...]>
  ├── commissionIndex: Map<...>
  ├── markupCatchAll: [RuleRuntime, ...]    ← regras genéricas sem campo indexável
  ├── commissionCatchAll: [...]
  ├── markupRules: [...]                    ← lista completa (fallback)
  ├── commissionRules: [...]
  ├── ruleById: Map<ruleId → RuleRuntime>
  └── compiledDrl: DroolsCompiler.Compiled  ← opcional

RuleRuntime (imutável)
  ├── ruleId, peso, ruleType, enabled
  ├── conditions: [RuleCondition, ...]       ← original
  ├── compiledConditions: [CompiledCondition, ...]
  │   └── CompiledCondition { field, op, kind(DATE|NUMBER|BOOLEAN|STRING|STRING_SET), scalarValue, setValues }
  ├── value: BigDecimal
  └── metadata: Map<String, String>
```

---

## 9. Patterns e Decisões de Arquitetura

### 9.1 Immutable Snapshot + Atomic Swap

| Aspecto | Detalhe |
|---|---|
| **Pattern** | Copy-on-Write / Immutable Value Object |
| **Implementação** | `AtomicReference<RuntimeSnapshot>` — `getAndSet()` lock-free |
| **Benefício** | Leituras (hot path) nunca bloqueiam. Swap é O(1). Zero contenção entre threads. |
| **Trade-off** | Memória dobra momentaneamente durante o swap (snapshot antigo + novo coexistem até GC). |

### 9.2 Outbox Pattern

| Aspecto | Detalhe |
|---|---|
| **Pattern** | Transactional Outbox |
| **Implementação** | Gravação do evento na tabela `outbox_events` na mesma transação da versão. Scheduler Quarkus faz polling 1s. |
| **Benefício** | Garante atomicidade entre persistência e publicação do evento. |
| **Evolução** | Em produção, substituir polling por Debezium CDC para latência sub-segundo. |

### 9.3 Evento Leve + Storage Compartilhado

| Aspecto | Detalhe |
|---|---|
| **Pattern** | Claim Check |
| **Implementação** | O evento Kafka carrega apenas `manifestPath` (~200 bytes). O consumer resolve os artefatos via storage. |
| **Benefício** | Eventos pequenos, sem limite de tamanho para o bundle de regras. |
| **Evolução** | Substituir filesystem local por S3/MinIO para ambientes distribuídos. |

### 9.4 Hash Index com Fallback

| Aspecto | Detalhe |
|---|---|
| **Pattern** | Multi-column hash index + catch-all merge |
| **Implementação** | `IndexBuilder` gera chaves combinatórias dos campos preferenciais com `equals`. Regras sem condição `equals` indexável vão para o catch-all. |
| **Benefício** | Lookup O(1) para a maioria dos casos. Regras genéricas nunca são perdidas. |

### 9.5 FieldTypeRegistry (Type-Ahead Compilation)

| Aspecto | Detalhe |
|---|---|
| **Pattern** | Schema inference + typed fast-path |
| **Implementação** | No load do snapshot, infere `Map<campo → ValueKind>` das `CompiledCondition`. Na preparação do item, faz parse apenas do tipo necessário. |
| **Benefício** | Elimina `try/catch` com exception no hot path. Campos string (maioria) pulam parse de date/number. |

### 9.6 Request-Scoped Match Cache

| Aspecto | Detalhe |
|---|---|
| **Pattern** | Deduplication via signature key |
| **Implementação** | `MatchKeyBuilder` gera chave usando **apenas campos que alguma regra referencia**. Items que diferem apenas em campos não-utilizados (ex: hotelId) compartilham a mesma avaliação. |
| **Benefício** | Reduz dramaticamente o número de avaliações em requests com itens repetitivos. |

### 9.7 Chunked Parallel Evaluation

| Aspecto | Detalhe |
|---|---|
| **Pattern** | Fork-Join / Map-Reduce |
| **Implementação** | Items são particionados em chunks (configurable). Cada chunk é avaliado em thread separada. Resultados são mergeados. Suporta virtual threads (Java 21). |
| **Configuração** | `engine.chunkSize=128`, `engine.parallelism=15`, `engine.useVirtualThreads=false` |

---

## 10. Inventário de Otimizações de Performance

| # | Otimização | Fase | Impacto |
|---|---|---|---|
| 1 | **CompiledCondition** — tipagem em load time | Build | Elimina inferência de tipo no matching |
| 2 | **FieldTypeRegistry** — campo→tipo no load | Build | Elimina exceptions no PreparedFieldValue (hot path) |
| 3 | **IndexBuilder** — hash index combinatório | Build | O(1) lookup em vez de scan linear de todas as regras |
| 4 | **PreparedFieldValue.fromTyped()** — parse seletivo | Prepare | Só parseia o tipo necessário; campos STRING pulam date/number |
| 5 | **PreparedItem key cache** — normalizedKey cache | Prepare | `ConcurrentHashMap` de chaves normalizadas entre requests |
| 6 | **MatchKeyBuilder** — cache por assinatura de campos ativos | Evaluate | Items redundantes reutilizam resultado anterior (zero matching) |
| 7 | **Sorted catch-all merge** — merge O(n) em vez de sort | Evaluate | Bucket + catch-all já ordenados; merge sem alocação de sort |
| 8 | **Chunk parallelism** — avaliação paralela | Evaluate | Utiliza múltiplos cores para requests com muitos items |
| 9 | **Immutable snapshot** — lock-free reads | Runtime | Zero contenção em leitura; swap atômico O(1) |
| 10 | **runtime.json pré-compilado** — artefato pronto | Activation | Consumer não precisa inferir tipos; lê diretamente `ValueKind` |
| 11 | **GraalVM JIT** — compilador Graal | JVM | -8% tail latency, 50% menos variância |

---

## 11. Resultados de Benchmark

### 11.1 HotSpot vs GraalVM JIT

> Cenário: 10 requests sequenciais, 2 items/request, pool de 50k regras

| Métrica | HotSpot C2 | GraalVM JIT | Δ % |
|---|---:|---:|---:|
| **Avg** | 71,7 ms | 69,7 ms | **−2,8%** |
| **P50** | 74 ms | 68 ms | **−8,1%** |
| **P95** | 87 ms | 80 ms | **−8,0%** |
| **P99** | 87 ms | 80 ms | **−8,0%** |
| **Spread (max−min)** | 28 ms | 14 ms | **−50%** |

**Conclusão**: GraalVM JIT entrega latência mais previsível com variância 50% menor.

### 11.2 Stress Test Concorrente

> Cenário: 1.000 items/request, pool de 50k regras, HotSpot JDK 21.0.10  
> Data: 13/03/2026

| Concorrência | Requests | Avg (ms) | P50 (ms) | P95 (ms) | P99 (ms) | Max (ms) | Throughput (req/s) | Throughput (items/s) | Erros |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 2.162 | 2.162 | 2.162 | 2.162 | 2.162 | 0,5 | 462 | 0 |
| 5 | 5 | 2.342 | 2.342 | 2.356 | 2.358 | 2.359 | 2,1 | 2.117 | 0 |
| 10 | 10 | 2.415 | 2.424 | 2.439 | 2.442 | 2.442 | 4,1 | 4.088 | 0 |
| 20 | 20 | 2.740 | 2.742 | 2.758 | 2.762 | 2.763 | 7,2 | 7.229 | 0 |
| 50 | 50 | 3.900 | 3.898 | 3.947 | 3.949 | 3.949 | 12,6 | 12.612 | 0 |

#### Análise

```
Throughput (items/s)                            Latência Avg (ms)
     │                                               │
12k+ │                            ●                   │
     │                                            4k  │                            ●
10k  │                                                │
     │                                            3k  │                 ●
 8k  │                                                │
     │                 ●                           2k  │  ●      ●      ●
 4k  │        ●                                       │
     │                                             1k  │
 2k  │  ●                                             │
     │                                                │
     └────────────────────────                        └────────────────────────
       1    5    10   20   50  (concorrência)           1    5    10   20   50
```

**Observações-chave**:

1. **Escalabilidade quase linear até 10 concorrentes**: throughput sobe de 462 → 4.088 items/s (8,8x com 10x concorrência).
2. **Degradação suave em 20-50**: throughput continua subindo (7,2k → 12,6k items/s), com latência individual subindo de ~2,4s → ~3,9s.
3. **Zero erros**: 50 requests simultâneas, cada uma com 1.000 items contra 50.000 regras — **50.000 items processados em paralelo** em 3,96 segundos. Nenhum erro, nenhum timeout.
4. **Tail latency contida**: P99-P50 = 51ms no cenário de 50 concorrentes. Sistema muito previsível.
5. **Pico de throughput medido**: **12.612 items/s** (cenário de 50 concorrentes, máquina de desenvolvimento).

### 11.3 Referência de Performance (single-request)

| Cenário | Latência |
|---|---|
| 1 request, 2 items, 50k regras | ~70 ms |
| 1 request, 1.000 items, 50k regras | ~150 ms (após warmup) |
| 1 request, 1.000 items, 50k regras (cold) | ~2.100 ms |

---

## 12. Escalabilidade e Dimensionamento

### 12.1 Escalabilidade Horizontal

```
                    ┌─────────────────┐
                    │   Load Balancer  │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
        ┌─────▼─────┐ ┌─────▼─────┐ ┌─────▼─────┐
        │ Engine #1 │ │ Engine #2 │ │ Engine #N │
        │ :8082     │ │ :8082     │ │ :8082     │
        │           │ │           │ │           │
        │ Kafka     │ │ Kafka     │ │ Kafka     │
        │ Consumer  │ │ Consumer  │ │ Consumer  │
        └───────────┘ └───────────┘ └───────────┘
              │              │              │
              └──────────────┴──────────────┘
                             │
                    ┌────────▼────────┐
                    │  Shared Storage  │
                    │  (S3 / NFS)     │
                    └─────────────────┘
```

| Eixo | Estratégia |
|---|---|
| **Mais throughput** | Adicionar réplicas do decision-engine (stateless em relação a banco). Kafka consumer group garante que todas recebem o mesmo evento. |
| **Mais regras** | O índice hash mantém lookup O(1) independente do número de regras. Impacto principal é na memória. |
| **Mais items/request** | Chunk parallelism distribui items entre cores. Virtual threads (Java 21) para I/O-bound futuro. |
| **Mais rulesets** | `ConcurrentHashMap<rulesetId, AtomicReference<Snapshot>>` — cada ruleset isolado. |

### 12.2 Requisitos de Recursos Estimados

| Recurso | Cenário 50k regras | Notas |
|---|---|---|
| Memória heap | ~200-400 MB | Snapshot imutável com índices + catch-all |
| CPU | 4-8 cores | Chunk parallelism; Quarkus + Kafka consumer |
| Storage | ~15 MB/versão | DRL + canonical + runtime + manifest |
| Kafka | 1 partition é suficiente (POC) | Produção: 1 partition por grupo de rulesets |

### 12.3 Pontos de Evolução para Produção

| Área | POC atual | Evolução sugerida |
|---|---|---|
| Storage | Filesystem local | S3 / MinIO / Azure Blob |
| Outbox | Polling 1s | Debezium CDC (latência <100ms) |
| Kafka | Single partition | Partition por rulesetId |
| Observabilidade | Logs + MetricsCollector | Distributed tracing (OpenTelemetry) |
| Cache L2 | Nenhum | Redis para cache cross-instance de resultados |
| Build nativo | Não validado | GraalVM native-image (startup <1s) |

---

## 13. Observabilidade

### 13.1 Métricas (Prometheus via Micrometer)

O decision-engine expõe métricas via `/q/metrics` (Quarkus Micrometer + Prometheus registry).

### 13.2 Métricas Internas (MetricsCollector)

Cada request coleta tempos por fase (em nanosegundos):

| Métrica | Descrição |
|---|---|
| `requestPreparation` | Tempo total de preparação de todos os items |
| `itemPreparation` | Tempo de normalização + tipagem de campos |
| `indexLookup` | Tempo de busca no índice hash |
| `ruleMatchingMarkup` | Tempo de matching de regras de markup |
| `ruleMatchingCommission` | Tempo de matching de regras de comissão |
| `mergeResult` | Tempo de merge dos resultados |
| `cacheHits` | Quantidade de avaliações evitadas pelo MatchKeyCache |
| `cacheSize` | Tamanho do cache no request |

### 13.3 Logs Estruturados

| Evento | Informação |
|---|---|
| Ativação de snapshot | rulesetId, version, checksum, fieldTypeRegistry size |
| Erro de ativação | rulesetId, version, causa (checksum mismatch, artefato faltante, etc.) |
| Rollback | rulesetId, version anterior → nova |

---

## 14. Riscos e Considerações

| Risco | Mitigação |
|---|---|
| **Memória**: snapshots grandes (>100k regras) | Monitorar heap; GC tuning; considerar off-heap para índices |
| **Storage local**: não funciona em ambiente distribuído | Migrar para S3/MinIO antes de produção |
| **Outbox polling 1s**: latência de ativação | Debezium CDC reduz para <100ms |
| **Drools no classpath**: reflexão impede build nativo | Flag `engine.drl.compile-on-activation=false` desativa; remover dependência se DRL não for necessário |
| **Single point of failure**: Kafka indisponível | Engine continua operando com último snapshot; resiliência nativa |
| **Consistência eventual**: janela entre publish e ativação | ~1-2s (outbox polling); aceitável para pricing; Debezium reduz para ms |

---

## 15. Glossário

| Termo | Definição |
|---|---|
| **Ruleset** | Conjunto de regras identificado por UUID. Pode ter múltiplas versões. |
| **Versão** | Snapshot publicado de um ruleset (imutável após publicação). |
| **Manifest** | JSON que descreve os artefatos de uma versão (checksums, caminhos). |
| **runtime.json** | Artefato pré-compilado com regras tipadas, pronto para consumo do engine. |
| **CompiledCondition** | Condição de regra com tipo resolvido (`STRING`, `NUMBER`, `DATE`, `BOOLEAN`, `STRING_SET`). |
| **FieldTypeRegistry** | Mapa `campo → ValueKind` construído no load. Elimina probing de tipo no hot path. |
| **RuntimeSnapshot** | Objeto imutável contendo tudo necessário para avaliação: regras, índices, registry. |
| **Atomic Swap** | Troca do snapshot ativo via `AtomicReference.getAndSet()` — lock-free. |
| **First-Win** | Estratégia de avaliação: primeira regra que faz match (por ordem de peso) vence. |
| **Catch-All** | Regras genéricas que não possuem condição `equals` indexável — avaliadas junto com o bucket do índice. |
| **MatchKey** | Chave de cache por item baseada apenas nos campos que regras referenciam. |
| **Outbox** | Tabela de eventos pendentes publicados na mesma transação que a versão. |
| **Peso** | Prioridade da regra (menor = mais específica = avaliada primeiro). |

---

> **Documento gerado em**: 13/03/2026  
> **Versão do sistema**: 2.0.0  
> **Ambiente de benchmark**: Windows, JDK 21.0.10, single machine (desenvolvimento)

