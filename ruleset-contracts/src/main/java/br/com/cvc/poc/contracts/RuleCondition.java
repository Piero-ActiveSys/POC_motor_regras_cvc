package br.com.cvc.poc.contracts;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleCondition(String campo, ConditionOp operacao, Object valor) {}
