package br.com.cvc.poc.engine.runtime;

import br.com.cvc.poc.contracts.ConditionOp;
import br.com.cvc.poc.contracts.RuleCondition;
import br.com.cvc.poc.contracts.RuleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class FieldTypeRegistryTest {

    @Test
    void emptyRegistryDefaultsToString() {
        var reg = FieldTypeRegistry.empty();
        assertEquals(CompiledCondition.ValueKind.STRING, reg.kindFor("anything"));
        assertEquals(0, reg.size());
    }

    @Test
    void buildFromRulesDetectsTypes() {
        var rules = List.of(
            rule(List.of(
                new RuleCondition("Broker", ConditionOp.equals, "Omnibees"),
                new RuleCondition("Checkout", ConditionOp.lte, "15/04/2026"),
                new RuleCondition("CafeDaManha", ConditionOp.equals, true),
                new RuleCondition("ValorMinimo", ConditionOp.gte, 100.0)
            ))
        );

        var reg = FieldTypeRegistry.build(rules);

        assertEquals(CompiledCondition.ValueKind.STRING, reg.kindFor("broker"));
        assertEquals(CompiledCondition.ValueKind.DATE, reg.kindFor("checkout"));
        assertEquals(CompiledCondition.ValueKind.BOOLEAN, reg.kindFor("cafedamanha"));
        assertEquals(CompiledCondition.ValueKind.NUMBER, reg.kindFor("valorminimo"));
        assertEquals(4, reg.size());
    }

    @Test
    void unknownFieldDefaultsToString() {
        var rules = List.of(
            rule(List.of(new RuleCondition("Broker", ConditionOp.equals, "Omnibees")))
        );
        var reg = FieldTypeRegistry.build(rules);

        // Field not in any rule → defaults to STRING
        assertEquals(CompiledCondition.ValueKind.STRING, reg.kindFor("hotelid"));
    }

    @Test
    void firstKindWinsForSameField() {
        // Two rules both use "Broker" as STRING — putIfAbsent keeps the first
        var rules = List.of(
            rule(List.of(new RuleCondition("Broker", ConditionOp.equals, "Omnibees"))),
            rule(List.of(new RuleCondition("Broker", ConditionOp.in, List.of("A", "B"))))
        );
        var reg = FieldTypeRegistry.build(rules);

        assertEquals(CompiledCondition.ValueKind.STRING, reg.kindFor("broker"));
    }

    private static RuleRuntime rule(List<RuleCondition> conditions) {
        return new RuleRuntime(
            "r1", 1, RuleType.MARKUP, true,
            conditions,
            conditions.stream().map(CompiledCondition::from).toList(),
            BigDecimal.ONE, Map.of()
        );
    }
}

