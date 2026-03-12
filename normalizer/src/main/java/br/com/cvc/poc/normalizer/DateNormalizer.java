package br.com.cvc.poc.normalizer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class DateNormalizer {
  private DateNormalizer() {}
  public static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  public static LocalDate parseBr(Object raw) {
    if (raw == null) return null;
    var s = String.valueOf(raw).trim();
    if (s.isEmpty()) return null;
    return LocalDate.parse(s, BR);
  }
}
