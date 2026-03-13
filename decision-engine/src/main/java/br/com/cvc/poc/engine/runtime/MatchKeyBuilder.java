package br.com.cvc.poc.engine.runtime;

import java.util.List;

/**
 * Builds a cache key for a PreparedItem using only the fields that are
 * actually referenced by rules. Fields that no rule cares about are excluded,
 * so items that differ only on irrelevant fields (e.g. hotelId, roomId)
 * produce the same key and share the cached evaluation result.
 *
 * The key is deterministic: activeFields is pre-sorted (TreeSet) and
 * the normalizedString of each field is used.
 */
public final class MatchKeyBuilder {
  private MatchKeyBuilder() {}

  /**
   * @param item          the prepared item
   * @param activeFields  sorted list of field names used by rules of a given type
   * @return a string key like "broker=omnibees|estado=ce|nonrefundable=false"
   *         or empty string if no active fields have values
   */
  public static String build(PreparedItem item, List<String> activeFields) {
    if (activeFields == null || activeFields.isEmpty()) return "";

    var sb = new StringBuilder(activeFields.size() * 24);
    boolean first = true;
    for (var field : activeFields) {
      var prepared = item.field(field);
      String val;
      if (prepared != null && prepared.normalizedString() != null) {
        val = prepared.normalizedString();
      } else {
        val = "";
      }
      if (!first) sb.append('|');
      sb.append(field).append('=').append(val);
      first = false;
    }
    return sb.toString();
  }
}

