package br.com.cvc.poc.contracts;

import java.math.BigDecimal;
import java.util.List;

public record AppliedDecision(
    String appliedRuleId,
    Integer pesoUsed,
    List<String> trace,
    String uniqueCheck,
    BigDecimal value
) {}
