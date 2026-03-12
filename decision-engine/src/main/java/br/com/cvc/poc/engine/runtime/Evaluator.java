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
    Map<String, List<RuleRuntime>> index = type == RuleType.MARKUP ? runtime.markupIndex() : runtime.commissionIndex();
    if (index != null && !index.isEmpty()) {
      for (String key : item.indexKeys()) {
        List<RuleRuntime> bucket = index.get(key);
        if (bucket != null && !bucket.isEmpty()) {
          return bucket;
        }
      }
    }
    return type == RuleType.MARKUP ? runtime.markupRules() : runtime.commissionRules();
  }
}
