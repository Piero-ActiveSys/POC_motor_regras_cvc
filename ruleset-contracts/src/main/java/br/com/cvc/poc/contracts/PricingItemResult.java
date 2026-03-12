package br.com.cvc.poc.contracts;
import java.math.BigDecimal;
public record PricingItemResult(String itemId, BigDecimal markup, BigDecimal commission, ItemAudit audit) {}
