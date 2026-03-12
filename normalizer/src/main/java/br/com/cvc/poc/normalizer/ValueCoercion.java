package br.com.cvc.poc.normalizer;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public final class ValueCoercion {
  private ValueCoercion() {}

  public static BigDecimal toBigDecimal(Object v) {
    if (v == null) return null;
    if (v instanceof BigDecimal bd) return bd;
    return new BigDecimal(String.valueOf(v).trim());
  }

  public static Double toDouble(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.doubleValue();
    return Double.parseDouble(String.valueOf(v).trim());
  }

  public static Long toLong(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.longValue();
    return Long.parseLong(String.valueOf(v).trim());
  }

  public static Boolean toBoolean(Object v) {
    if (v == null) return null;
    if (v instanceof Boolean b) return b;

    var s = String.valueOf(v).trim().toLowerCase();

    // true-like
    if (s.equals("true") || s.equals("1") || s.equals("sim") || s.equals("yes")) return true;

    // false-like
    if (s.equals("false") || s.equals("0") || s.equals("nao") || s.equals("não") || s.equals("no")) return false;

    // not a boolean
    return null;
  }

  public static List<String> toStringList(Object v) {
    if (v == null) return List.of();
    if (v instanceof Collection<?> c) return c.stream().map(String::valueOf).toList();
    var s = String.valueOf(v).trim();
    if (s.contains(";")) return List.of(s.split("\\s*;\\s*"));
    if (s.contains(",")) return List.of(s.split("\\s*,\\s*"));
    return List.of(s);
  }
}
