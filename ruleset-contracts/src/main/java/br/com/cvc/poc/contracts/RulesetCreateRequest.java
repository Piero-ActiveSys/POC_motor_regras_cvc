package br.com.cvc.poc.contracts;
import jakarta.validation.constraints.NotBlank;
public record RulesetCreateRequest(@NotBlank String name, @NotBlank String createdBy) {}
