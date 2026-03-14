package br.com.cvc.poc.engine.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable map of field → ValueKind, built once at snapshot load.
 * Used by PreparedFieldValue to skip unnecessary (and expensive) type probing.
 * <p>
 * Fields not present in any rule default to STRING.
 */
public final class FieldTypeRegistry {

  private static final FieldTypeRegistry EMPTY = new FieldTypeRegistry(Map.of());

  private final Map<String, CompiledCondition.ValueKind> types;

  private FieldTypeRegistry(Map<String, CompiledCondition.ValueKind> types) {
    this.types = types;
  }

  /**
   * Scans all compiled conditions and records the first ValueKind seen for each field.
   * No conflict handling needed — the frontend guarantees a single type per field.
   */
  public static FieldTypeRegistry build(List<RuleRuntime> allRules) {
    var map = new HashMap<String, CompiledCondition.ValueKind>();
    for (var rule : allRules) {
      for (var cond : rule.compiledConditions()) {
        if (cond.field() != null && !cond.field().isBlank()) {
          map.putIfAbsent(cond.field(), cond.kind());
        }
      }
    }
    return new FieldTypeRegistry(Map.copyOf(map));
  }

  public static FieldTypeRegistry empty() {
    return EMPTY;
  }

  /** Returns the known kind for this field, or STRING if not mapped. */
  public CompiledCondition.ValueKind kindFor(String field) {
    return types.getOrDefault(field, CompiledCondition.ValueKind.STRING);
  }

  public Map<String, CompiledCondition.ValueKind> types() {
    return types;
  }

  public int size() {
    return types.size();
  }
}

