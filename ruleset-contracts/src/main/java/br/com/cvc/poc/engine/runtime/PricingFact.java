package br.com.cvc.poc.engine.runtime;

import java.util.Map;

public class PricingFact {
  private final Map<String, Object> fields;
  public PricingFact(Map<String, Object> fields) { this.fields = fields; }
  public Map<String, Object> getFields() { return fields; }
}
