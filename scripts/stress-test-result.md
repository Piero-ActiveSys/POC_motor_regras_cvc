

\# Resultados — Stress Test Concorrente (2026-03-13)



> \*\*Cenário\*\*: 1k items/request, pool de 50k regras, HotSpot JDK 21.0.10



\## Tabela de Resultados



| Concorrência | Requests | Avg (ms) | P50 (ms) | P95 (ms) | P99 (ms) | Max (ms) | Throughput (req/s) | Throughput (items/s) | Erros |

|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|

| 1 | 1 | 2.162 | 2.162 | 2.162 | 2.162 | 2.162 | \*\*0,5\*\* | \*\*462\*\* | 0 |

| 5 | 5 | 2.342 | 2.342 | 2.356 | 2.358 | 2.359 | \*\*2,1\*\* | \*\*2.117\*\* | 0 |

| 10 | 10 | 2.415 | 2.424 | 2.439 | 2.442 | 2.442 | \*\*4,1\*\* | \*\*4.088\*\* | 0 |

| 20 | 20 | 2.740 | 2.742 | 2.758 | 2.762 | 2.763 | \*\*7,2\*\* | \*\*7.229\*\* | 0 |

| 50 | 50 | 3.900 | 3.898 | 3.947 | 3.949 | 3.949 | \*\*12,6\*\* | \*\*12.612\*\* | 0 |



\## Análise



\### 1. Escalabilidade quase linear até 10 concorrentes

\- 1→5: throughput sobe de \*\*462 → 2.117 items/s\*\* (4,6x com 5x concorrência)

\- 5→10: \*\*2.117 → 4.088 items/s\*\* (1,9x com 2x concorrência)

\- Latência média subiu apenas \*\*12%\*\* de 1→10 (2.162 → 2.415ms)



\### 2. Degradação suave com 20-50 concorrentes

\- 10→20: throughput \*\*4.088 → 7.229 items/s\*\* (1,8x)

\- 20→50: throughput \*\*7.229 → 12.612 items/s\*\* (1,7x)

\- Latência média subiu de 2.4s → 3.9s — mas o \*\*throughput total\*\* continua subindo



\### 3. Zero erros em todos os cenários

\- \*\*50 requests simultâneas\*\*, cada uma com \*\*1.000 items\*\* contra \*\*50k regras\*\*

\- = \*\*50.000 items processados em paralelo\*\*, todos retornando HTTP 200

\- Tempo total (wall): apenas \*\*3.96 segundos\*\*



\### 4. Tail latency extremamente contida

\- Spread P50→P99 no cenário de 50 concorrentes: apenas \*\*51ms\*\* (3.898 → 3.949)

\- O sistema é \*\*muito previsível\*\* mesmo sob carga



\### 5. Números-chave para produção

| Métrica | Valor |

|---|---|

| \*\*Pico de throughput medido\*\* | \*\*12.612 items/s\*\* |

| \*\*Throughput com 10 concorrentes\*\* | \*\*4.088 items/s\*\* |

| \*\*Latência por request (1k items) sob carga\*\* | \*\*\~2.4-3.9s\*\* |

| \*\*Latência por item (estimada)\*\* | \*\*\~2.4ms/item\*\* |

| \*\*Erros sob carga\*\* | \*\*0%\*\* |

| \*\*Variância (P99-P50 com 50 conc.)\*\* | \*\*51ms\*\* |





