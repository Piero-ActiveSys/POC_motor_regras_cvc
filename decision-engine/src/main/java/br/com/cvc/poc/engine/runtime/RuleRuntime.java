package br.com.cvc.poc.engine.runtime;

import br.com.cvc.poc.contracts.RuleCondition;
import br.com.cvc.poc.contracts.RuleType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RuleRuntime(
    String ruleId,
    int peso,
    RuleType ruleType,
    boolean enabled,
    List<RuleCondition> conditions,
    List<CompiledCondition> compiledConditions,
    BigDecimal value,
    Map<String, String> metadata
) {}
