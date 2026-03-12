package br.com.cvc.poc.rulescrud.application;

import br.com.cvc.poc.contracts.RuleDefinition;

import java.util.List;

/**
 * DRL único com uma regra por peso (salience = -peso).
 * A condição real é delegada para RuleMatcher.match(ruleId, factFields),
 * permitindo campos dinâmicos e operadores.
 */
public final class DrlBuilder {
  private DrlBuilder() {}

  public static String build(List<RuleDefinition> rules) {
    var sb = new StringBuilder();
    sb.append("package br.com.cvc.poc.rules.generated;\n\n");
    sb.append("import br.com.cvc.poc.engine.runtime.PricingFact;\n");
    sb.append("import br.com.cvc.poc.engine.runtime.MatchAccumulator;\n");
    sb.append("import br.com.cvc.poc.engine.runtime.RuleMatcher;\n");
    sb.append("import java.math.BigDecimal;\n\n");
    sb.append("global MatchAccumulator accumulator;\n\n");

    for (var r : rules) {
      if (!r.enabled()) continue;
      sb.append("rule \"").append(r.ruleType()).append("__PESO_").append(r.peso()).append("__").append(r.ruleId()).append("\"\n");
      sb.append("  salience ").append(-r.peso()).append("\n");
      sb.append("when\n");
      sb.append("  $f : PricingFact()\n");
      sb.append("  eval(RuleMatcher.match(\"").append(r.ruleId()).append("\", $f.getFields()))\n");
      sb.append("then\n");
      sb.append("  accumulator.trySet(\"").append(r.ruleType()).append("\", ").append(r.peso()).append(", \"").append(r.ruleId()).append("\", new BigDecimal(\"").append(r.value()).append("\"));\n");
      sb.append("end\n\n");
    }
    return sb.toString();
  }
}
