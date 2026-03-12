package br.com.cvc.poc.engine.application;

import br.com.cvc.poc.contracts.ItemAudit;
import br.com.cvc.poc.contracts.PricingItem;
import br.com.cvc.poc.contracts.PricingItemResult;
import br.com.cvc.poc.contracts.PricingRequest;
import br.com.cvc.poc.contracts.PricingResponse;
import br.com.cvc.poc.contracts.RuleType;
import br.com.cvc.poc.engine.infra.kafka.RulesetUpdatesConsumer;
import br.com.cvc.poc.engine.metrics.MetricsCollector;
import br.com.cvc.poc.engine.runtime.Evaluator;
import br.com.cvc.poc.engine.runtime.PreparedItem;
import br.com.cvc.poc.engine.runtime.RulesetRuntime;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class PricingService {

  @Inject RulesetUpdatesConsumer consumer;

  @ConfigProperty(name = "engine.chunkSize", defaultValue = "128")
  int chunkSize;

  @ConfigProperty(name = "engine.parallelism", defaultValue = "0")
  int parallelism;

  @ConfigProperty(name = "engine.useVirtualThreads", defaultValue = "false")
  boolean useVirtualThreads;

  @ConfigProperty(name = "engine.audit.trace.enabled", defaultValue = "false")
  boolean traceEnabled;

  private ExecutorService executor;

  @PostConstruct
  void init() {
    int configuredParallelism = effectiveParallelism();
    executor = useVirtualThreads
        ? Executors.newVirtualThreadPerTaskExecutor()
        : Executors.newFixedThreadPool(configuredParallelism);
  }

  @PreDestroy
  void shutdown() {
    if (executor != null) executor.shutdown();
  }

  public PricingResponse calculate(PricingRequest req) {
    var runtime = consumer.registry().current(req.rulesetId());
    List<PricingItem> requestItems = req.items();

    ChunkEvalResult finalResult;
    if (requestItems.size() <= effectiveChunkSize() || effectiveParallelism() == 1) {
      finalResult = evalChunk(runtime, requestItems);
    } else {
      var futures = new ArrayList<CompletableFuture<ChunkEvalResult>>();
      for (var chunk : chunk(requestItems, effectiveChunkSize())) {
        futures.add(CompletableFuture.supplyAsync(() -> evalChunk(runtime, chunk), executor));
      }

      var mergedItems = new ArrayList<PricingItemResult>(requestItems.size());
      var mergedMetrics = new MetricsCollector();
      for (var future : futures) {
        var partial = future.join();
        mergedItems.addAll(partial.results());
        mergedMetrics.mergeFrom(partial.metrics());
      }
      finalResult = new ChunkEvalResult(mergedItems, mergedMetrics);
    }

    finalResult.metrics().printSummary();
    return new PricingResponse(req.requestId(), req.rulesetId(), runtime.version(), runtime.checksum(), finalResult.results());
  }

  private int effectiveChunkSize() {
    return Math.max(1, chunkSize);
  }

  private int effectiveParallelism() {
    return parallelism <= 0 ? Runtime.getRuntime().availableProcessors() : parallelism;
  }

  private ChunkEvalResult evalChunk(RulesetRuntime runtime, List<PricingItem> items) {
    var out = new ArrayList<PricingItemResult>(items.size());
    var metrics = new MetricsCollector();

    for (var item : items) {
      long prepareStart = System.nanoTime();
      PreparedItem preparedItem = PreparedItem.from(item, runtime.preferredIndexFields());
      long preparedElapsed = System.nanoTime() - prepareStart;
      metrics.addRequestPreparationNanos(preparedElapsed);
      metrics.addItemPreparationNanos(preparedElapsed);

      var markup = Evaluator.evaluate(runtime, RuleType.MARKUP, preparedItem, metrics, traceEnabled);
      var commission = Evaluator.evaluate(runtime, RuleType.COMMISSION, preparedItem, metrics, traceEnabled);

      long mergeStart = System.nanoTime();
      var audit = new ItemAudit(markup, commission, List.of());
      out.add(new PricingItemResult(
          preparedItem.itemId(),
          markup.value() == null ? BigDecimal.ZERO : markup.value(),
          commission.value() == null ? BigDecimal.ZERO : commission.value(),
          audit
      ));
      metrics.addMergeResultNanos(System.nanoTime() - mergeStart);
    }

    return new ChunkEvalResult(out, metrics);
  }

  private static <T> List<List<T>> chunk(List<T> items, int size) {
    if (items.isEmpty()) return List.of();
    var out = new ArrayList<List<T>>();
    for (int i = 0; i < items.size(); i += size) {
      out.add(items.subList(i, Math.min(items.size(), i + size)));
    }
    return out;
  }

  private record ChunkEvalResult(List<PricingItemResult> results, MetricsCollector metrics) {
  }
}
