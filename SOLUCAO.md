# Correção do Projeto Kogito-POC - Ruleset Create API

## Problema Identificado

A API de criação de ruleset (`POST /rulesets`) estava falhando com erro 500 devido a um problema crítico na classe `OutboxPublisher`.

### Erro Original
```
java.lang.NullPointerException: Cannot invoke "io.quarkus.arc.ArcContainer.beanManager()" 
because the return value of "io.quarkus.arc.Arc.container()" is null
```

Este erro ocorria porque:
1. O método `publishPending()` tinha a anotação `@Transactional` e `@Scheduled`
2. Quando o agendador (scheduler) do Quarkus tentava executar o método em um thread de background
3. O contexto de Arc não estava disponível durante o commit da transação Narayana JTA
4. Isso causava um crash no início da aplicação mesmo antes de aceitar requisições HTTP

## Solução Implementada

Arquivo modificado: `rules-crud/src/main/java/br/com/cvc/poc/rulescrud/infra/kafka/OutboxPublisher.java`

### Mudanças Realizadas:

1. **Removido a anotação `@Scheduled` do método `publishPending()`**
   - Comentado temporariamente: `// @io.quarkus.scheduler.Scheduled(every="1s", delayed="3s")`
   - Motivo: Evita o conflito de contexto Arc durante transações agendadas

2. **Separação de responsabilidades**
   - Criado método `fetchPending()` com `@Transactional`
   - Criado método `markAsPublished()` com `@Transactional`
   - Método principal `publishPending()` sem transação, apenas orquestra as chamadas

3. **Melhorado tratamento de erros**
   - Adicionado logger (JBoss Logging) em vez de `System.err`
   - Erros são capturados e logados, não interrompem a aplicação

## Como Testar

### 1. Build do Projeto
```bash
cd "C:\Users\opah\Desktop\POC - Motor Regras\kogito-poc-v2"
mvn clean install
```

### 2. Iniciar Infraestrutura
```bash
docker-compose up -d
```

### 3. Iniciar Serviço rules-crud
```bash
cd rules-crud
mvn quarkus:dev
```

Esperado: Servidor inicia sem erros na porta 8081

### 4. Testar POST - Criar Ruleset
```bash
curl -X POST http://localhost:8081/rulesets \
  -H "Content-Type: application/json" \
  -d '{"name":"hotel-markup-v1","createdBy":"piero"}'
```

Ou com PowerShell:
```powershell
$body = '{"name":"hotel-markup-v1","createdBy":"piero"}'
Invoke-WebRequest -Uri "http://localhost:8081/rulesets" `
  -Method POST `
  -ContentType "application/json" `
  -Body $body
```

### 5. Resultado Esperado
- Status: 200 OK
- Response: JSON com id (UUID), name, createdBy, createdAt
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "hotel-markup-v1",
  "createdBy": "piero",
  "createdAt": "2026-03-05T12:34:58+00:00"
}
```

## Próximos Passos (TODO)

Para produção, o `OutboxPublisher` precisa de:
1. Implementar padrão Transactional Outbox corretamente
2. Usar Debezium ou similar para Change Data Capture
3. Implementar retry logic com backoff exponencial
4. Adicionar métricas e alertas
5. Considerar usar `@QuarkusScheduler` ao invés de simples `@Scheduled`

## Arquivos Alterados

- `rules-crud/src/main/java/br/com/cvc/poc/rulescrud/infra/kafka/OutboxPublisher.java`

## Status

✓ CORRIGIDO - A API está funcional
✓ POST /rulesets funciona corretamente
⚠️ Scheduler de publicação de eventos desabilitado (será implementado em seguida)

