package br.com.cvc.poc.engine.runtime;

import java.util.List;
import java.util.Map;

public record RulesetRuntime(
    String rulesetId,
    long version,
    String checksum,
    List<String> preferredIndexFields,
    List<RuleRuntime> markupRules,
    List<RuleRuntime> commissionRules,
    Map<String, List<RuleRuntime>> markupIndex,
    Map<String, List<RuleRuntime>> commissionIndex
) {
  public static RulesetRuntime empty(String id) {
    return new RulesetRuntime(id, 0L, "empty", List.of(), List.of(), List.of(), Map.of(), Map.of());
  }
}
