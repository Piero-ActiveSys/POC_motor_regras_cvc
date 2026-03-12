package br.com.cvc.poc.contracts;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RuleDefinition(
    String ruleId,
    String rulesetId,
    int peso,
    RuleType ruleType,
    boolean enabled,
    List<RuleCondition> regras,
    BigDecimal value,
    Map<String, String> metadata
) {}
