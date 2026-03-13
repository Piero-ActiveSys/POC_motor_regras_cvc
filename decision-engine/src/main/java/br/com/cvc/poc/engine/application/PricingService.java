package br.com.cvc.poc.engine.application;

import br.com.cvc.poc.contracts.AppliedDecision;
import br.com.cvc.poc.contracts.ItemAudit;
import br.com.cvc.poc.contracts.PricingItem;
import br.com.cvc.poc.contracts.PricingItemResult;
import br.com.cvc.poc.contracts.PricingRequest;
import br.com.cvc.poc.contracts.PricingResponse;
import br.com.cvc.poc.contracts.RuleType;
import br.com.cvc.poc.engine.infra.kafka.RulesetUpdatesConsumer;
import br.com.cvc.poc.engine.metrics.MetricsCollector;
import br.com.cvc.poc.engine.runtime.Evaluator;
import br.com.cvc.poc.engine.runtime.MatchKeyBuilder;
import br.com.cvc.poc.engine.runtime.PreparedItem;
import br.com.cvc.poc.engine.runtime.RulesetRuntime;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
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

  @ConfigProperty(name = "engine.normalization.enabled", defaultValue = "false")
  boolean normalizationEnabled;

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

    // Request-scoped caches: keyed by active-fields signature, per rule type.
    // Items that differ only on fields no rule uses (e.g. hotelId, roomId)
    // will share the same cache entry.
    var markupCache = new HashMap<String, AppliedDecision>(256);
    var commissionCache = new HashMap<String, AppliedDecision>(256);

    var activeMarkupFields = runtime.activeMarkupFields();
    var activeCommissionFields = runtime.activeCommissionFields();

    int cacheHits = 0;

    for (var item : items) {
      long prepareStart = System.nanoTime();
      PreparedItem preparedItem = normalizationEnabled
          ? PreparedItem.from(item, runtime.preferredIndexFields())
          : PreparedItem.fromPreNormalized(item, runtime.preferredIndexFields());
      long preparedElapsed = System.nanoTime() - prepareStart;
      metrics.addRequestPreparationNanos(preparedElapsed);
      metrics.addItemPreparationNanos(preparedElapsed);

      // --- MARKUP ---
      String markupKey = MatchKeyBuilder.build(preparedItem, activeMarkupFields);
      AppliedDecision markup = markupCache.get(markupKey);
      if (markup == null) {
        markup = Evaluator.evaluate(runtime, RuleType.MARKUP, preparedItem, metrics, traceEnabled);
        markupCache.put(markupKey, markup);
      } else {
        cacheHits++;
      }

      // --- COMMISSION ---
      String commissionKey = MatchKeyBuilder.build(preparedItem, activeCommissionFields);
      AppliedDecision commission = commissionCache.get(commissionKey);
      if (commission == null) {
        commission = Evaluator.evaluate(runtime, RuleType.COMMISSION, preparedItem, metrics, traceEnabled);
        commissionCache.put(commissionKey, commission);
      } else {
        cacheHits++;
      }

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

    metrics.addCacheHits(cacheHits);
    metrics.addCacheSize(markupCache.size() + commissionCache.size());

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
