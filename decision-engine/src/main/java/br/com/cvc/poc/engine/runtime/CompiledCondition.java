package br.com.cvc.poc.engine.runtime;

import br.com.cvc.poc.contracts.ConditionOp;
import br.com.cvc.poc.contracts.RuleCondition;
import br.com.cvc.poc.normalizer.DateNormalizer;
import br.com.cvc.poc.normalizer.TextNormalizer;
import br.com.cvc.poc.normalizer.ValueCoercion;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record CompiledCondition(
    String field,
    ConditionOp op,
    ValueKind kind,
    Object scalarValue,
    Set<String> setValues
) {
  public enum ValueKind { DATE, NUMBER, BOOLEAN, STRING, STRING_SET }

  public static CompiledCondition from(RuleCondition condition) {
    var field = TextNormalizer.normKey(condition.campo());
    if (field == null || condition.operacao() == null) {
      return new CompiledCondition(field, condition.operacao(), ValueKind.STRING, null, Set.of());
    }

    Object raw = condition.valor();
    if (isEmpty(raw)) {
      return new CompiledCondition(field, condition.operacao(), ValueKind.STRING, null, Set.of());
    }

    if (condition.operacao() == ConditionOp.in) {
      Set<String> vals = new LinkedHashSet<>();
      for (var item : ValueCoercion.toStringList(raw)) {
        var normalized = TextNormalizer.normValue(item);
        if (normalized != null && !normalized.isBlank()) vals.add(normalized);
      }
      return new CompiledCondition(field, condition.operacao(), ValueKind.STRING_SET, null, vals);
    }

    LocalDate date = tryParseDate(raw);
    if (date != null) {
      return new CompiledCondition(field, condition.operacao(), ValueKind.DATE, date, Set.of());
    }

    Double number = tryParseNumber(raw);
    if (number != null) {
      return new CompiledCondition(field, condition.operacao(), ValueKind.NUMBER, number, Set.of());
    }

    Boolean bool = ValueCoercion.toBoolean(raw);
    if (bool != null) {
      return new CompiledCondition(field, condition.operacao(), ValueKind.BOOLEAN, bool, Set.of());
    }

    return new CompiledCondition(field, condition.operacao(), ValueKind.STRING, TextNormalizer.normValue(raw), Set.of());
  }

  private static boolean isEmpty(Object value) {
    if (value == null) return true;
    if (value instanceof String s) return s.trim().isEmpty();
    if (value instanceof List<?> list) return list.isEmpty();
    return false;
  }

  private static LocalDate tryParseDate(Object v) {
    try { return DateNormalizer.parseBr(v); }
    catch (Exception e) { return null; }
  }

  private static Double tryParseNumber(Object v) {
    try { return ValueCoercion.toDouble(v); }
    catch (Exception e) { return null; }
  }
}
