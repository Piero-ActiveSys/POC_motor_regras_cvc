package br.com.cvc.poc.contracts;

import java.util.Map;

/**
 * Describes a published version bundle. Written as manifest.json on storage.
 * Single point of entry for activation in the decision-engine.
 */
public record ManifestDto(
    String rulesetId,
    long version,
    String eventType,
    String generatedAt,
    boolean normalizationApplied,
    String checksum,
    Map<String, String> files
) {
    /** Standard file keys used in the files map. */
    public static final String FILE_DRL = "drl";
    public static final String FILE_CANONICAL = "canonical";
    public static final String FILE_RUNTIME = "runtime";
}

