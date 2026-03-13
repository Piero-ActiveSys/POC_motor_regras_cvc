package br.com.cvc.poc.engine.runtime;

import br.com.cvc.poc.normalizer.DateNormalizer;
import br.com.cvc.poc.normalizer.TextNormalizer;
import br.com.cvc.poc.normalizer.ValueCoercion;

import java.time.LocalDate;

public record PreparedFieldValue(
    Object raw,
    String normalizedString,
    Double numberValue,
    LocalDate dateValue,
    Boolean booleanValue
) {
  public static PreparedFieldValue from(Object raw) {
    return new PreparedFieldValue(
        raw,
        TextNormalizer.normValue(raw),
        tryParseNumber(raw),
        tryParseDate(raw),
        ValueCoercion.toBoolean(raw)
    );
  }

  /**
   * Fast path: assumes the value is already normalized (lowercase, no accents).
   * Skips TextNormalizer.normValue and uses raw toString directly.
   */
  public static PreparedFieldValue fromPreNormalized(Object raw) {
    String strValue = raw == null ? null : String.valueOf(raw).trim();
    return new PreparedFieldValue(
        raw,
        strValue,
        tryParseNumber(raw),
        tryParseDate(raw),
        ValueCoercion.toBoolean(raw)
    );
  }

  private static LocalDate tryParseDate(Object v) {
    try { return DateNormalizer.parseBr(v); }
    catch (Exception e) { return null; }
  }

  private static Double tryParseNumber(Object v) {
    try { return ValueCoercion.toDouble(v); }
    catch (Exception e) { return null; }
  }
}
