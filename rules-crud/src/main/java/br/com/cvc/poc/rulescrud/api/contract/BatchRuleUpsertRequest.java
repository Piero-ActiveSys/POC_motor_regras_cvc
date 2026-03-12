package br.com.cvc.poc.rulescrud.api.contract;

import br.com.cvc.poc.contracts.RuleUpsertRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BatchRuleUpsertRequest(
    @NotEmpty List<@Valid @NotNull RuleUpsertRequest> rules,
    @Min(1) Integer chunkSize
) {
  public int resolvedChunkSize() {
    return chunkSize == null || chunkSize < 1 ? 1000 : chunkSize;
  }
}
