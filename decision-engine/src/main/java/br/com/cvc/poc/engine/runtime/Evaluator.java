package br.com.cvc.poc.engine.runtime;

import br.com.cvc.poc.contracts.AppliedDecision;
import br.com.cvc.poc.contracts.RuleType;
import br.com.cvc.poc.engine.metrics.MetricsCollector;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Evaluator {

  private Evaluator() {
  }

  public static AppliedDecision evaluate(
      RulesetRuntime runtime,
      RuleType type,
      PreparedItem item,
      MetricsCollector metrics,
      boolean traceEnabled
  ) {
    List<String> trace = traceEnabled ? new ArrayList<>() : List.of();

    long lookupStart = System.nanoTime();
    List<RuleRuntime> candidates = candidates(runtime, type, item);
    metrics.addIndexLookupNanos(System.nanoTime() - lookupStart);

    long matchStart = System.nanoTime();

    for (var rule : candidates) {
      if (!rule.enabled()) {
        continue;
      }

      if (!DynamicMatcher.matchesAll(rule, item)) {
        if (traceEnabled) {
          trace.add("no match peso=" + rule.peso());
        }
        continue;
      }

      long elapsed = System.nanoTime() - matchStart;
      if (type == RuleType.MARKUP) {
        metrics.addRuleMatchingMarkupNanos(elapsed);
      } else {
        metrics.addRuleMatchingCommissionNanos(elapsed);
      }

      if (traceEnabled) {
        trace.add("match peso=" + rule.peso());
      }

      return new AppliedDecision(
          rule.ruleId(),
          rule.peso(),
          trace,
          "OK",
          rule.value() == null ? BigDecimal.ZERO : rule.value()
      );
    }

    long elapsed = System.nanoTime() - matchStart;
    if (type == RuleType.MARKUP) {
      metrics.addRuleMatchingMarkupNanos(elapsed);
    } else {
      metrics.addRuleMatchingCommissionNanos(elapsed);
    }

    if (traceEnabled) {
      trace.add("no match any");
    }

    return new AppliedDecision(
        null,
        null,
        trace,
        "NO_MATCH",
        BigDecimal.ZERO
    );
  }

  private static List<RuleRuntime> candidates(RulesetRuntime runtime, RuleType type, PreparedItem item) {
    boolean isMarkup = type == RuleType.MARKUP;
    Map<String, List<RuleRuntime>> index = isMarkup ? runtime.markupIndex() : runtime.commissionIndex();
    List<RuleRuntime> catchAll = isMarkup ? runtime.markupCatchAll() : runtime.commissionCatchAll();

    // Try to find the best indexed bucket
    List<RuleRuntime> bucket = null;
    if (index != null && !index.isEmpty()) {
      for (String key : item.indexKeys()) {
        List<RuleRuntime> found = index.get(key);
        if (found != null && !found.isEmpty()) {
          bucket = found;
          break;
        }
      }
    }

    // If no bucket found and no catch-all, fall back to full list
    if (bucket == null && catchAll.isEmpty()) {
      return isMarkup ? runtime.markupRules() : runtime.commissionRules();
    }

    // If only one side has rules, return it directly (avoid merge allocation)
    if (bucket == null || bucket.isEmpty()) return catchAll;
    if (catchAll.isEmpty()) return bucket;

    // Merge bucket + catch-all in peso order (both are already sorted)
    var merged = new ArrayList<RuleRuntime>(bucket.size() + catchAll.size());
    int i = 0, j = 0;
    while (i < bucket.size() && j < catchAll.size()) {
      var b = bucket.get(i);
      var c = catchAll.get(j);
      if (b.peso() < c.peso() || (b.peso() == c.peso() && b.ruleId().compareTo(c.ruleId()) <= 0)) {
        merged.add(b);
        i++;
      } else {
        merged.add(c);
        j++;
      }
    }
    while (i < bucket.size()) merged.add(bucket.get(i++));
    while (j < catchAll.size()) merged.add(catchAll.get(j++));
    return merged;
  }
}
