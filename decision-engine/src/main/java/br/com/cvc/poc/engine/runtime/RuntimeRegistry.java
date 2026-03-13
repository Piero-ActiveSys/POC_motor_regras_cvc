package br.com.cvc.poc.engine.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class RuntimeRegistry {
  private final ConcurrentHashMap<String, AtomicReference<RuntimeSnapshot>> map = new ConcurrentHashMap<>();

  public RuntimeSnapshot currentSnapshot(String rulesetId) {
    return map.computeIfAbsent(rulesetId, id -> new AtomicReference<>(RuntimeSnapshot.empty(id))).get();
  }

  /** Backward compat: returns RulesetRuntime view from active snapshot. */
  public RulesetRuntime current(String rulesetId) {
    return currentSnapshot(rulesetId).toRulesetRuntime();
  }

  /**
   * Atomic swap: replaces the active snapshot for a rulesetId.
   * Returns the previous snapshot.
   */
  public RuntimeSnapshot swap(String rulesetId, RuntimeSnapshot next) {
    return map.computeIfAbsent(rulesetId, id -> new AtomicReference<>(RuntimeSnapshot.empty(id))).getAndSet(next);
  }
}
