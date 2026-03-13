package br.com.cvc.poc.contracts;

/**
 * Lean response for the /publish endpoint.
 * Does NOT contain DRL or canonical JSON content.
 */
public record PublishResponse(
    String rulesetId,
    long version,
    String eventType,
    String publishedAt,
    String checksum,
    String manifestPath
) {}

