package br.com.cvc.poc.rulescrud.application;

import br.com.cvc.poc.contracts.RuleDefinition;
import br.com.cvc.poc.contracts.RuleUpsertRequest;
import br.com.cvc.poc.contracts.RuleCondition;
import br.com.cvc.poc.normalizer.TextNormalizer;
import br.com.cvc.poc.rulescrud.infra.db.RuleEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class RuleCanonicalizer {
  @Inject ObjectMapper mapper;

  public String conditionsJson(RuleUpsertRequest req) {
    try {
      var normalized = normalizeConditions(req.regras());
      return mapper.writeValueAsString(normalized);
    }
    catch (Exception e) { throw new RuntimeException(e); }
  }

  /** Original method — always normalizes (backward compat). */
  public RuleDefinition toDefinition(RuleEntity e) {
    return toDefinition(e, true);
  }

  /** Normalization-aware method. */
  public RuleDefinition toDefinition(RuleEntity e, boolean normalize) {
    try {
      var conditions = mapper.readValue(e.conditionsJson, new TypeReference<List<RuleCondition>>() {});
      if (normalize) {
        conditions = normalizeConditions(conditions);
      }
      var md = Map.of("createdBy", e.createdBy, "createdAt", String.valueOf(e.createdAt));
      return new RuleDefinition(
          e.id.toString(),
          e.rulesetId.toString(),
          e.peso,
          br.com.cvc.poc.contracts.RuleType.valueOf(e.ruleType),
          e.enabled,
          conditions,
          e.value,
          md
      );
    } catch (Exception ex) { throw new RuntimeException(ex); }
  }

  private static List<RuleCondition> normalizeConditions(List<RuleCondition> conditions) {
    if (conditions == null) return List.of();
    return conditions.stream()
        .map(c -> new RuleCondition(
            TextNormalizer.normKey(c.campo()),
            c.operacao(),
            c.valor()
        ))
        // remove any condition with null/empty campo after normalization
        .filter(c -> c.campo() != null && !c.campo().isBlank())
        .collect(Collectors.toList());
  }
}
