package br.com.cvc.poc.rulescrud.application;

import br.com.cvc.poc.contracts.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DrlBuilderTest {
  @Test
  void buildsDrl() {
    var r = new RuleDefinition("r1","rs",2, RuleType.MARKUP,true,
        List.of(new RuleCondition("Broker", ConditionOp.equals, "Junipper")),
        new BigDecimal("0.80"),
        Map.of("createdBy","x","createdAt","now")
    );
    var drl = DrlBuilder.build(List.of(r));
    assertTrue(drl.contains("PESO_2"));
    assertTrue(drl.contains("RuleMatcher.match"));
  }
}
