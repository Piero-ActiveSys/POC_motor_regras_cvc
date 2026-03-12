package br.com.cvc.poc.rulescrud.api.contract;

import java.util.UUID;

public record BatchRuleUpsertResponse(
    UUID rulesetId,
    int requestedCount,
    int processedCount,
    int chunkSize,
    long elapsedMs
) {}
