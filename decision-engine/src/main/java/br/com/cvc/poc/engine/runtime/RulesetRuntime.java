package br.com.cvc.poc.engine.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record RulesetRuntime(
    String rulesetId,
    long version,
    String checksum,
    List<String> preferredIndexFields,
    List<RuleRuntime> markupRules,
    List<RuleRuntime> commissionRules,
    Map<String, List<RuleRuntime>> markupIndex,
    Map<String, List<RuleRuntime>> commissionIndex,
    List<RuleRuntime> markupCatchAll,
    List<RuleRuntime> commissionCatchAll,
    List<String> activeMarkupFields,
    List<String> activeCommissionFields,
    FieldTypeRegistry fieldTypeRegistry
) {
  public static RulesetRuntime empty(String id) {
    return new RulesetRuntime(id, 0L, "empty", List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), FieldTypeRegistry.empty());
  }
}
