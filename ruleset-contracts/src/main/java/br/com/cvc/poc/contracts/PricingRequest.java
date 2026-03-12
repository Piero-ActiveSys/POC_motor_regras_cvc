package br.com.cvc.poc.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PricingRequest(
    @NotBlank String requestId,
    @NotBlank String rulesetId,
    @NotEmpty @Valid List<PricingItem> items
) {}
