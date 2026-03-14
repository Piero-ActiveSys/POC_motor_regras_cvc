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

  /**
   * Typed path: only parses the representation needed by the given hint.
   * Eliminates exception-based probing for types that won't be used.
   */
  public static PreparedFieldValue fromTyped(Object raw, CompiledCondition.ValueKind hint) {
    String normalized = TextNormalizer.normValue(raw);
    return switch (hint) {
      case STRING, STRING_SET -> new PreparedFieldValue(raw, normalized, null, null, null);
      case NUMBER  -> new PreparedFieldValue(raw, normalized, tryParseNumber(raw), null, null);
      case DATE    -> new PreparedFieldValue(raw, normalized, null, tryParseDate(raw), null);
      case BOOLEAN -> new PreparedFieldValue(raw, normalized, null, null, ValueCoercion.toBoolean(raw));
    };
  }

  /**
   * Typed + pre-normalized path: combines both optimizations.
   */
  public static PreparedFieldValue fromPreNormalizedTyped(Object raw, CompiledCondition.ValueKind hint) {
    String strValue = raw == null ? null : String.valueOf(raw).trim();
    return switch (hint) {
      case STRING, STRING_SET -> new PreparedFieldValue(raw, strValue, null, null, null);
      case NUMBER  -> new PreparedFieldValue(raw, strValue, tryParseNumber(raw), null, null);
      case DATE    -> new PreparedFieldValue(raw, strValue, null, tryParseDate(raw), null);
      case BOOLEAN -> new PreparedFieldValue(raw, strValue, null, null, ValueCoercion.toBoolean(raw));
    };
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
