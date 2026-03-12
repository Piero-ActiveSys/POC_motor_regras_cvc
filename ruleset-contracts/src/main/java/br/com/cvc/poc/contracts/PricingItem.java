package br.com.cvc.poc.contracts;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;

public class PricingItem {
  @NotBlank
  public String itemId;

  @NotNull
  public Integer qntdePax;

  private final Map<String, Object> fields = new HashMap<>();

  @JsonAnySetter
  public void set(String key, Object value) {
    if ("itemId".equalsIgnoreCase(key) || "qntdePax".equalsIgnoreCase(key)) return;
    fields.put(key, value);
  }

  @JsonAnyGetter
  public Map<String, Object> any() { return fields; }

  public Map<String, Object> fields() { return fields; }
}
