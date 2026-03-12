package br.com.cvc.poc.engine.runtime;

import java.math.BigDecimal;

/** Guard: first match wins per type, in order of salience (peso). */
public interface MatchAccumulator {
  void trySet(String ruleType, int peso, String ruleId, BigDecimal value);
}
