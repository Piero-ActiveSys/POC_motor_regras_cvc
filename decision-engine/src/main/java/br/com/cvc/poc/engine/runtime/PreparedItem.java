package br.com.cvc.poc.engine.runtime;

import br.com.cvc.poc.contracts.PricingItem;
import br.com.cvc.poc.normalizer.TextNormalizer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record PreparedItem(
    String itemId,
    Map<String, Object> rawFields,
    Map<String, PreparedFieldValue> preparedFields,
    List<String> indexKeys
) {
  private static final ConcurrentHashMap<String, String> KEY_CACHE = new ConcurrentHashMap<>();
  private static final String QNTDE_PAX_KEY = normalizedKey("QntdePax");

  public static PreparedItem from(PricingItem item, List<String> preferredIndexFields) {
    var sourceFields = item.fields();
    int expectedSize = sourceFields.size() + 1;

    var raw = new HashMap<String, Object>(Math.max(16, expectedSize * 2));
    for (var entry : sourceFields.entrySet()) {
      var normalizedKey = normalizedKey(entry.getKey());
      if (normalizedKey != null) {
        raw.put(normalizedKey, entry.getValue());
      }
    }
    raw.put(QNTDE_PAX_KEY, item.qntdePax);

    var prepared = new HashMap<String, PreparedFieldValue>(Math.max(16, raw.size() * 2));
    for (var entry : raw.entrySet()) {
      prepared.put(entry.getKey(), PreparedFieldValue.from(entry.getValue()));
    }

    var keys = IndexBuilder.keysForSearch(prepared, preferredIndexFields);
    return new PreparedItem(item.itemId, raw, prepared, keys);
  }

  public boolean hasField(String field) {
    return preparedFields.containsKey(field);
  }

  public PreparedFieldValue field(String field) {
    return preparedFields.get(field);
  }

  private static String normalizedKey(String key) {
    if (key == null) return null;
    return KEY_CACHE.computeIfAbsent(key, TextNormalizer::normKey);
  }
}
