package br.com.cvc.poc.engine.runtime;

import br.com.cvc.poc.contracts.ConditionOp;
import br.com.cvc.poc.contracts.RuleCondition;
import br.com.cvc.poc.contracts.RuleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DynamicMatcherTest {

  @Test
  void missingFieldSkipsRule() {
    var rule = rule(List.of(
        new RuleCondition("Broker", ConditionOp.equals, "Junipper"),
        new RuleCondition("CafeDaManha", ConditionOp.equals, true)
    ));
    var item = prepared(Map.of("Broker", "Junipper"));
    assertFalse(DynamicMatcher.matchesAll(rule, item));
  }

  @Test
  void dateLteMatches() {
    var rule = rule(List.of(new RuleCondition("Checkout", ConditionOp.lte, "26/10/2026")));
    var item = prepared(Map.of("Checkout", "25/10/2026"));
    assertTrue(DynamicMatcher.matchesAll(rule, item));
  }

  @Test
  void inMatches() {
    var rule = rule(List.of(new RuleCondition("Broker", ConditionOp.in, List.of("Junipper", "HotelBeds"))));
    var item = prepared(Map.of("Broker", "Junipper"));
    assertTrue(DynamicMatcher.matchesAll(rule, item));
  }

  private static RuleRuntime rule(List<RuleCondition> conditions) {
    return new RuleRuntime(
        "r1",
        1,
        RuleType.MARKUP,
        true,
        conditions,
        conditions.stream().map(CompiledCondition::from).toList(),
        BigDecimal.ONE,
        Map.of()
    );
  }

  private static PreparedItem prepared(Map<String, Object> fields) {
    var raw = new java.util.HashMap<String, Object>();
    var prepared = new java.util.HashMap<String, PreparedFieldValue>();
    for (var entry : fields.entrySet()) {
      var key = br.com.cvc.poc.normalizer.TextNormalizer.normKey(entry.getKey());
      raw.put(key, entry.getValue());
      prepared.put(key, PreparedFieldValue.from(entry.getValue()));
    }
    return new PreparedItem("item", Map.copyOf(raw), Map.copyOf(prepared), List.of());
  }
}
