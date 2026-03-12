package br.com.cvc.poc.engine.runtime;

import br.com.cvc.poc.contracts.RuleType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

public class FirstWinAccumulator implements br.com.cvc.poc.engine.runtime.MatchAccumulator {
  private final Map<RuleType, Result> map = new EnumMap<>(RuleType.class);

  @Override
  public void trySet(String ruleType, int peso, String ruleId, BigDecimal value) {
    var t = RuleType.valueOf(ruleType);
    map.computeIfAbsent(t, __ -> new Result(ruleId, peso, value));
  }

  public Result get(RuleType type) { return map.get(type); }

  public record Result(String ruleId, int peso, BigDecimal value) {}
}
