package br.com.cvc.poc.engine.runtime;

import br.com.cvc.poc.contracts.RuleType;
import br.com.cvc.poc.normalizer.TextNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IndexBuilder {
  private IndexBuilder() {}

  public static BuiltIndex build(List<RuleRuntime> rules, List<String> preferredFields) {
    var pref = normalizePreferredFields(preferredFields);
    Map<String, List<RuleRuntime>> markup = new LinkedHashMap<>();
    Map<String, List<RuleRuntime>> commission = new LinkedHashMap<>();

    for (var rule : rules) {
      if (!rule.enabled()) continue;
      var exactValues = extractIndexValues(rule, pref);
      if (exactValues.isEmpty()) continue;

      for (var key : expandKeys(exactValues, pref)) {
        if (rule.ruleType() == RuleType.MARKUP) {
          markup.computeIfAbsent(key, __ -> new ArrayList<>()).add(rule);
        } else {
          commission.computeIfAbsent(key, __ -> new ArrayList<>()).add(rule);
        }
      }
    }

    sortBuckets(markup);
    sortBuckets(commission);
    return new BuiltIndex(pref, Map.copyOf(markup), Map.copyOf(commission));
  }

  public static List<String> keysForSearch(Map<String, PreparedFieldValue> preparedFields, List<String> preferredFields) {
    if (preparedFields == null || preparedFields.isEmpty() || preferredFields == null || preferredFields.isEmpty()) {
      return List.of();
    }

    var parts = new ArrayList<String>(preferredFields.size());
    for (var field : preferredFields) {
      var prepared = preparedFields.get(field);
      if (prepared == null) continue;
      var normalized = prepared.normalizedString();
      if (normalized == null || normalized.isBlank()) continue;
      parts.add(field + "=" + normalized);
    }
    if (parts.isEmpty()) return List.of();

    return expandOrderedSearchKeys(parts);
  }

  private static List<String> expandOrderedSearchKeys(List<String> orderedParts) {
    int n = orderedParts.size();
    int total = (1 << n) - 1;
    var out = new ArrayList<String>(total);

    for (int take = n; take >= 1; take--) {
      for (int mask = total; mask >= 1; mask--) {
        if (Integer.bitCount(mask) != take) continue;
        var sb = new StringBuilder(take * 24);
        boolean first = true;
        for (int i = 0; i < n; i++) {
          int bit = 1 << i;
          if ((mask & bit) == 0) continue;
          if (!first) sb.append('|');
          sb.append(orderedParts.get(i));
          first = false;
        }
        out.add(sb.toString());
      }
    }
    return out;
  }

  private static void sortBuckets(Map<String, List<RuleRuntime>> buckets) {
    for (var entry : buckets.entrySet()) {
      entry.getValue().sort(java.util.Comparator.comparingInt(RuleRuntime::peso).thenComparing(RuleRuntime::ruleId));
      entry.setValue(List.copyOf(entry.getValue()));
    }
  }

  private static Map<String, String> extractIndexValues(RuleRuntime rule, List<String> preferredFields) {
    var byField = new LinkedHashMap<String, String>();
    for (var condition : rule.compiledConditions()) {
      if (!preferredFields.contains(condition.field())) continue;
      if (condition.kind() == CompiledCondition.ValueKind.STRING_SET) continue;
      if (condition.op() != br.com.cvc.poc.contracts.ConditionOp.equals) continue;
      if (condition.scalarValue() == null) continue;
      byField.put(condition.field(), scalarToIndexValue(condition));
    }
    return byField;
  }

  private static String scalarToIndexValue(CompiledCondition condition) {
    return switch (condition.kind()) {
      case DATE, NUMBER, BOOLEAN, STRING -> TextNormalizer.normValue(condition.scalarValue());
      case STRING_SET -> null;
    };
  }

  private static List<String> expandKeys(Map<String, String> values, List<String> preferredFields) {
    var orderedParts = new ArrayList<String>(preferredFields.size());
    for (var field : preferredFields) {
      var value = values.get(field);
      if (value != null && !value.isBlank()) {
        orderedParts.add(field + "=" + value);
      }
    }
    if (orderedParts.isEmpty()) {
      return List.of();
    }
    return expandOrderedSearchKeys(orderedParts);
  }

  private static List<String> normalizePreferredFields(List<String> preferredFields) {
    return preferredFields == null ? List.of() : preferredFields.stream()
        .map(TextNormalizer::normKey)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  public record BuiltIndex(
      List<String> preferredFields,
      Map<String, List<RuleRuntime>> markupIndex,
      Map<String, List<RuleRuntime>> commissionIndex
  ) {}
}
