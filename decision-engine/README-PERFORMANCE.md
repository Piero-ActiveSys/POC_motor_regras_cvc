# Decision Engine refatorado para performance

## Principais mudanças

- Separação de runtime por tipo de regra: `MARKUP` e `COMMISSION`
- Pré-compilação das condições das regras no consumo do evento Kafka
- Pré-normalização dos itens recebidos em `/price/calculate`
- Índice por combinações parciais dos campos preferidos, com busca do mais específico para o menos específico
- Reutilização do executor da aplicação, sem criar/destruir pool a cada request
- Trace detalhado desabilitado por padrão para reduzir alocação e GC

## Configuração sugerida

```properties
engine.chunkSize=128
engine.parallelism=8
engine.useVirtualThreads=false
engine.audit.trace.enabled=false
engine.index.fields=Broker,Cidade,Estado,hotelId
```

## Observações

- As regras continuam sendo avaliadas de forma síncrona dentro do endpoint `/price/calculate`
- O fallback para full scan ainda existe, mas agora só entra quando o índice não encontra candidatos
- Para que a indexação reflita as mudanças, publique novamente o ruleset após subir a aplicação
