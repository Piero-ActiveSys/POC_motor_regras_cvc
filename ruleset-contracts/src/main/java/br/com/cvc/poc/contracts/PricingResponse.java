package br.com.cvc.poc.contracts;
import java.util.List;
public record PricingResponse(String requestId, String rulesetId, long version, String checksum, List<PricingItemResult> results) {}
