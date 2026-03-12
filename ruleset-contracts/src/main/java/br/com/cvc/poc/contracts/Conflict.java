package br.com.cvc.poc.contracts;
import java.util.List;
public record Conflict(RuleType type, int peso, List<String> candidateRuleIds) {}
