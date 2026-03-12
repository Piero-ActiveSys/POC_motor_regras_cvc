package br.com.cvc.poc.contracts;
import jakarta.validation.constraints.NotBlank;
public record PublishRequest(@NotBlank String publishedBy) {}
