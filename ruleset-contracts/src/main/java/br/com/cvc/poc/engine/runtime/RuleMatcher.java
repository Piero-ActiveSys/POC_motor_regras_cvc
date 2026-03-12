package br.com.cvc.poc.engine.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Drools chama RuleMatcher.match(ruleId, factFields).
 * A função real é injetada pelo Decision Engine no hot-reload.
 */
public final class RuleMatcher {
  private RuleMatcher() {}

  private static final ConcurrentHashMap<String, BiPredicate<String, Map<String,Object>>> MATCHERS = new ConcurrentHashMap<>();

  public static void install(String rulesetId, BiPredicate<String, Map<String,Object>> matcher) {
    MATCHERS.put(rulesetId, matcher);
  }

  public static boolean match(String ruleId, Map<String, Object> factFields) {
    var rulesetId = (String) factFields.get("__rulesetId");
    if (rulesetId == null) return false;
    var m = MATCHERS.get(rulesetId);
    if (m == null) return false;
    return m.test(ruleId, factFields);
  }
}
