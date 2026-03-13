package br.com.cvc.poc.contracts;

import java.util.List;

/**
 * Pre-processed runtime payload written as runtime.json.
 * Ready for direct consumption by the decision-engine with minimal processing.
 */
public record RuntimeDto(
    String rulesetId,
    long version,
    List<String> preferredIndexFields,
    List<RuntimeRuleDto> markupRules,
    List<RuntimeRuleDto> commissionRules
) {
    /**
     * Individual rule already normalized, sorted, and with compiled condition hints.
     */
    public record RuntimeRuleDto(
        String ruleId,
        int peso,
        String ruleType,
        boolean enabled,
        List<RuntimeConditionDto> conditions,
        String value,
        java.util.Map<String, String> metadata
    ) {}

    /**
     * Pre-compiled condition ready for the engine matcher.
     */
    public record RuntimeConditionDto(
        String field,
        String op,
        String kind,
        Object scalarValue,
        List<String> setValues
    ) {}
}

