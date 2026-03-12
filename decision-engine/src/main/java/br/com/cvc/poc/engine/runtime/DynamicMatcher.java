package br.com.cvc.poc.engine.runtime;

import br.com.cvc.poc.contracts.ConditionOp;

import java.time.LocalDate;
import java.util.Objects;

public final class DynamicMatcher {
  private DynamicMatcher() {}

  public static boolean matchesAll(RuleRuntime rule, PreparedItem item) {
    for (var condition : rule.compiledConditions()) {
      var field = condition.field();
      if (field == null || field.isBlank()) return false;
      if (!item.hasField(field)) return false;
      if (condition.scalarValue() == null && condition.setValues().isEmpty()) continue;
      if (!eval(condition, item.field(field))) return false;
    }
    return true;
  }

  private static boolean eval(CompiledCondition condition, PreparedFieldValue searchValue) {
    return switch (condition.kind()) {
      case DATE -> compareComparable(searchValue.dateValue(), (LocalDate) condition.scalarValue(), condition.op());
      case NUMBER -> compareComparable(searchValue.numberValue(), (Double) condition.scalarValue(), condition.op());
      case BOOLEAN -> compareBoolean(searchValue.booleanValue(), (Boolean) condition.scalarValue(), condition.op());
      case STRING -> compareComparable(searchValue.normalizedString(), (String) condition.scalarValue(), condition.op());
      case STRING_SET -> compareSet(searchValue.normalizedString(), condition.setValues(), condition.op());
    };
  }

  private static boolean compareBoolean(Boolean search, Boolean rule, ConditionOp op) {
    if (search == null || rule == null) return false;
    return switch (op) {
      case equals -> Objects.equals(search, rule);
      case not_equals -> !Objects.equals(search, rule);
      default -> false;
    };
  }

  private static boolean compareSet(String search, java.util.Set<String> ruleSet, ConditionOp op) {
    if (search == null) return false;
    return switch (op) {
      case in -> ruleSet.contains(search);
      case not_equals -> !ruleSet.contains(search);
      default -> false;
    };
  }

  private static <T extends Comparable<T>> boolean compareComparable(T search, T rule, ConditionOp op) {
    if (search == null || rule == null || op == null) return false;
    int cmp = search.compareTo(rule);
    return switch (op) {
      case lt -> cmp < 0;
      case lte -> cmp <= 0;
      case gt -> cmp > 0;
      case gte -> cmp >= 0;
      case equals -> cmp == 0;
      case not_equals -> cmp != 0;
      case in -> false;
    };
  }
}
