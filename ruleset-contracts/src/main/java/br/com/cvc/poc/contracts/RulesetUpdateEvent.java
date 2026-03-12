package br.com.cvc.poc.contracts;

import java.util.List;

public record RulesetUpdateEvent(
    String eventId,
    String rulesetId,
    long version,
    String checksum,
    String publishedAt,
    Payload payload
) {
  public record Payload(String drl, List<RuleDefinition> rules) {}
}
