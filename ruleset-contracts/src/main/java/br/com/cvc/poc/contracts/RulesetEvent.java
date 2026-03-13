package br.com.cvc.poc.contracts;

/**
 * Lightweight Kafka event — no DRL or rules payload.
 * The consumer resolves the bundle via manifestPath on shared storage.
 */
public record RulesetEvent(
    int schemaVersion,
    RulesetEventType eventType,
    String rulesetId,
    long version,
    String checksum,
    String publishedAt,
    String manifestPath
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

