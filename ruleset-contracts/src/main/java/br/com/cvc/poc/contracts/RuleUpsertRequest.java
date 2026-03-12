package br.com.cvc.poc.contracts;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleUpsertRequest(
    @NotNull Integer peso,
    @NotNull RuleType ruleType,
    @NotNull Boolean enabled,
    @NotEmpty List<RuleCondition> regras,
    @NotNull BigDecimal value,
    @NotBlank String createdBy
) {}
