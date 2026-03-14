package br.com.cvc.poc.engine.runtime;
import br.com.cvc.poc.contracts.ManifestDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
/**
 * Immutable snapshot of everything needed for pricing evaluation.
 * Swapped atomically - all fields belong to the same version.
 */
public record RuntimeSnapshot(
    String rulesetId,
    long version,
    String checksum,
    Instant loadedAt,
    List<String> preferredIndexFields,
    List<RuleRuntime> markupRules,
    List<RuleRuntime> commissionRules,
    Map<String, List<RuleRuntime>> markupIndex,
    Map<String, List<RuleRuntime>> commissionIndex,
    List<RuleRuntime> markupCatchAll,
    List<RuleRuntime> commissionCatchAll,
    Map<String, RuleRuntime> ruleById,
    DroolsCompiler.Compiled compiledDrl,
    ManifestDto manifest,
    FieldTypeRegistry fieldTypeRegistry
) {
    public static RuntimeSnapshot empty(String rulesetId) {
        return new RuntimeSnapshot(
            rulesetId, 0L, "empty", Instant.now(),
            List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), Map.of(),
            null, null, FieldTypeRegistry.empty()
        );
    }
    /** Backward compat: create RulesetRuntime view. */
    public RulesetRuntime toRulesetRuntime() {
        return new RulesetRuntime(rulesetId, version, checksum, preferredIndexFields,
                markupRules, commissionRules, markupIndex, commissionIndex,
                markupCatchAll, commissionCatchAll,
                extractActiveFields(markupRules),
                extractActiveFields(commissionRules),
                fieldTypeRegistry);
    }

    /** Collect the sorted, distinct set of field names used in conditions of given rules. */
    private static List<String> extractActiveFields(List<RuleRuntime> rules) {
        var fields = new TreeSet<String>();
        for (var rule : rules) {
            for (var cond : rule.compiledConditions()) {
                if (cond.field() != null && !cond.field().isBlank()) {
                    fields.add(cond.field());
                }
            }
        }
        return List.copyOf(fields); // sorted, immutable
    }
}
