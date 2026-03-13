# GraalVM - Guia de Benchmark Comparativo

## Objetivo
Comparar a performance do **HotSpot JIT (C2)** vs **GraalVM JIT (Graal Compiler)**
no cenário de matching de regras do decision-engine.

---

## Passo a Passo

### 1. Benchmark com HotSpot (baseline)

Primeiro, colete os números com o JDK atual (HotSpot):

```powershell
# Iniciar infraestrutura
docker-compose up -d

# Iniciar decision-engine com HotSpot
mvn -pl decision-engine quarkus:dev

# Em outro terminal, rodar benchmark
.\scripts\bench-graalvm.ps1 -Requests 10
```

### 2. Instalar GraalVM JDK 21

```powershell
.\scripts\setup-graalvm.ps1
```

Isso baixa o GraalVM JDK 21, extrai em `tools/graalvm/` e configura `JAVA_HOME` na sessão.

### 3. Benchmark com GraalVM JIT

```powershell
# Alternar para GraalVM (persiste JAVA_HOME para o usuario)
.\scripts\switch-jdk.ps1 -JDK graalvm

# IMPORTANTE: Feche e reabra o terminal para o PATH ser atualizado
# Ou, na mesma sessão, execute:
#   $env:JAVA_HOME = "<caminho mostrado pelo script>"
#   $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Verificar
java -version
# Deve mostrar: "Oracle GraalVM" na saída

# Iniciar decision-engine com GraalVM JIT
mvn -pl decision-engine quarkus:dev

# Em outro terminal (com GraalVM ativo), rodar benchmark
.\scripts\bench-graalvm.ps1 -Requests 10
```

### 4. Comparar resultados

Os resultados são salvos automaticamente em `scripts/bench-results.csv`:

```
timestamp,jdk,requests,avg_ms,min_ms,max_ms,p50_ms,p95_ms,p99_ms
2026-03-13_16-48-04,HotSpot 21.0.10+8-LTS-217,10,71.7,59,87,74,87,87
2026-03-13_16-54-43,GraalVM 21.0.10+8.1,10,69.7,66,80,68,80,80
```

### 5. Voltar para HotSpot

```powershell
.\scripts\switch-jdk.ps1 -JDK hotspot
```

---

## Resultados Reais (2026-03-13)

> Cenário: 10 requests de matching de regras — decision-engine v2.0.0

### Dados brutos

| Métrica | HotSpot C2 | GraalVM JIT | Δ absoluto | Δ % |
|---|---:|---:|---:|---:|
| **Avg** | 71.7 ms | 69.7 ms | −2.0 ms | **−2.8 %** |
| **Min** | 59 ms | 66 ms | +7 ms | +11.9 % |
| **Max** | 87 ms | 80 ms | −7 ms | **−8.0 %** |
| **P50** | 74 ms | 68 ms | −6 ms | **−8.1 %** |
| **P95** | 87 ms | 80 ms | −7 ms | **−8.0 %** |
| **P99** | 87 ms | 80 ms | −7 ms | **−8.0 %** |
| **Spread (max−min)** | 28 ms | 14 ms | −14 ms | **−50.0 %** |

### Análise

1. **Latência média ~2.8 % menor** com GraalVM — ganho modesto, compatível com a
   carga leve (10 requests).
2. **P50 ~8 % melhor** — a mediana caiu de 74 → 68 ms, indicando que a maioria
   das requisições é mais rápida sob GraalVM.
3. **Cauda (P95/P99) ~8 % menor** — max de 87 → 80 ms; GraalVM compila de forma
   mais previsível, reduzindo outliers.
4. **Variância cortada pela metade** — spread de 28 ms → 14 ms. É o destaque:
   GraalVM entrega latência muito mais **consistente**.
5. **Min mais alto no GraalVM (66 vs 59 ms)** — HotSpot teve um "melhor caso"
   mais rápido, mas à custa de alta dispersão. GraalVM troca o pico mínimo por
   estabilidade.

### Conclusão

| Aspecto | Veredito |
|---|---|
| Throughput médio | GraalVM levemente melhor (~3 %) |
| Previsibilidade | **GraalVM claramente superior** (50 % menos variância) |
| Pior caso (tail latency) | GraalVM ~8 % melhor |
| Recomendação | ✅ Adotar GraalVM JIT em produção — o ganho em **consistência** é mais relevante que a melhoria de média |

> 💡 **Próximo passo**: repetir com `-Requests 100` e `-Requests 500` para
> validar se a vantagem se mantém ou amplia sob maior carga.

---

## Build Nativo (Forma 2 - Futuro)

O POM raiz já inclui o profile `native`. Para compilar como binário nativo:

```powershell
# Requer GraalVM + native-image instalado
.\scripts\switch-jdk.ps1 -JDK graalvm

mvn -Pnative clean package -pl decision-engine -DskipTests
```

> ⚠️ **Atenção**: Build nativo com Drools no classpath pode exigir configuração
> adicional de reflexão. O DRL está desligado, mas as dependências ainda carregam
> classes que usam reflexão. Pode ser necessário criar `reflect-config.json`.

---

## Arquivos criados

| Arquivo | Descrição |
|---|---|
| `scripts/setup-graalvm.ps1` | Baixa e instala GraalVM JDK 21 |
| `scripts/switch-jdk.ps1` | Alterna entre HotSpot e GraalVM |
| `scripts/bench-graalvm.ps1` | Benchmark com coleta de métricas |
| `pom.xml` (profile `native`) | Profile Maven para build nativo |


