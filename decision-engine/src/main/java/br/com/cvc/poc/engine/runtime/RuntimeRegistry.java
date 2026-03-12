package br.com.cvc.poc.engine.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class RuntimeRegistry {
  private final ConcurrentHashMap<String, AtomicReference<RulesetRuntime>> map = new ConcurrentHashMap<>();

  public RulesetRuntime current(String rulesetId) {
    return map.computeIfAbsent(rulesetId, id -> new AtomicReference<>(RulesetRuntime.empty(id))).get();
  }

  public void swap(String rulesetId, RulesetRuntime next) {
    map.computeIfAbsent(rulesetId, id -> new AtomicReference<>(RulesetRuntime.empty(id))).set(next);
  }
}
