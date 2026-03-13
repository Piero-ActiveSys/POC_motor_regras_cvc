package br.com.cvc.poc.engine.runtime;

import br.com.cvc.poc.contracts.RuleType;
import br.com.cvc.poc.normalizer.TextNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IndexBuilder {
  private IndexBuilder() {}

  private static final Comparator<RuleRuntime> RULE_ORDER =
      Comparator.comparingInt(RuleRuntime::peso).thenComparing(RuleRuntime::ruleId);

  public static BuiltIndex build(List<RuleRuntime> rules, List<String> preferredFields) {
    var pref = normalizePreferredFields(preferredFields);
    Map<String, List<RuleRuntime>> markup = new LinkedHashMap<>();
    Map<String, List<RuleRuntime>> commission = new LinkedHashMap<>();

    // Catch-all: rules that cannot be indexed (no equals on preferred fields)
    var markupCatchAll = new ArrayList<RuleRuntime>();
    var commissionCatchAll = new ArrayList<RuleRuntime>();

    for (var rule : rules) {
      if (!rule.enabled()) continue;
      var exactValues = extractIndexValues(rule, pref);
      boolean isMarkup = rule.ruleType() == RuleType.MARKUP;

      if (exactValues.isEmpty()) {
        // Rule has no indexable condition → catch-all
        if (isMarkup) markupCatchAll.add(rule);
        else commissionCatchAll.add(rule);
        continue;
      }

      for (var key : expandKeys(exactValues, pref)) {
        if (isMarkup) {
          markup.computeIfAbsent(key, __ -> new ArrayList<>()).add(rule);
        } else {
          commission.computeIfAbsent(key, __ -> new ArrayList<>()).add(rule);
        }
      }
    }

    // Sort catch-all lists
    markupCatchAll.sort(RULE_ORDER);
    commissionCatchAll.sort(RULE_ORDER);

    sortBuckets(markup);
    sortBuckets(commission);

    return new BuiltIndex(pref,
        Map.copyOf(markup), Map.copyOf(commission),
        List.copyOf(markupCatchAll), List.copyOf(commissionCatchAll));
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
      entry.getValue().sort(RULE_ORDER);
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
      Map<String, List<RuleRuntime>> commissionIndex,
      List<RuleRuntime> markupCatchAll,
      List<RuleRuntime> commissionCatchAll
  ) {}
}
