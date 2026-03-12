package br.com.cvc.poc.contracts;
import java.util.List;
public record ItemAudit(AppliedDecision markup, AppliedDecision commission, List<Conflict> conflicts) {}
