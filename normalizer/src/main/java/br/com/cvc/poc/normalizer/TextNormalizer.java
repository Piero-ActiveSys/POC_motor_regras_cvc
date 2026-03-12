package br.com.cvc.poc.normalizer;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {
  private TextNormalizer() {}

  public static String normKey(String s) {
    if (s == null) return null;
    var t = s.trim().toLowerCase(Locale.ROOT);
    if (t.isEmpty()) return null;

    // Remove accents/diacritics
    var n = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");

    // Normalize separators to underscore and keep only [a-z0-9_]
    n = n.replaceAll("[^a-z0-9]+", "_");
    n = n.replaceAll("^_+|_+$", "");
    return n.isEmpty() ? null : n;
  }

  public static String normValue(Object raw) {
    if (raw == null) return null;
    var s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
    var n = Normalizer.normalize(s, Normalizer.Form.NFD);
    return n.replaceAll("\\p{M}+", "");
  }
}
