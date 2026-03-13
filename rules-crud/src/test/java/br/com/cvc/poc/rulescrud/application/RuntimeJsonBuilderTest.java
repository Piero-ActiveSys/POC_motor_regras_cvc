package br.com.cvc.poc.rulescrud.application;

import br.com.cvc.poc.contracts.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RuntimeJsonBuilderTest {

    private final RuntimeJsonBuilder builder = new RuntimeJsonBuilder();

    @Test
    void buildsSeparatedByType() {
        var markup = new RuleDefinition("r1", "rs", 0, RuleType.MARKUP, true,
                List.of(new RuleCondition("Broker", ConditionOp.equals, "Juniper")),
                new BigDecimal("0.80"), Map.of());
        var commission = new RuleDefinition("r2", "rs", 1, RuleType.COMMISSION, true,
                List.of(new RuleCondition("Cidade", ConditionOp.equals, "Santos")),
                new BigDecimal("0.10"), Map.of());

        var result = builder.build("rs", 1, List.of(markup, commission), List.of("Broker", "Cidade"));

        assertEquals("rs", result.rulesetId());
        assertEquals(1, result.version());
        assertEquals(1, result.markupRules().size());
        assertEquals(1, result.commissionRules().size());
        assertEquals("r1", result.markupRules().get(0).ruleId());
        assertEquals("r2", result.commissionRules().get(0).ruleId());
    }

    @Test
    void normalizesIndexFields() {
        var rule = new RuleDefinition("r1", "rs", 0, RuleType.MARKUP, true,
                List.of(new RuleCondition("Broker", ConditionOp.equals, "Juniper")),
                new BigDecimal("0.80"), Map.of());

        var result = builder.build("rs", 1, List.of(rule), List.of("Broker", "Cidade", "Estado", "hotelId"));

        assertTrue(result.preferredIndexFields().contains("broker"));
        assertTrue(result.preferredIndexFields().contains("cidade"));
        assertTrue(result.preferredIndexFields().contains("estado"));
        assertTrue(result.preferredIndexFields().contains("hotelid"));
    }

    @Test
    void handlesDateCondition() {
        var rule = new RuleDefinition("r1", "rs", 0, RuleType.MARKUP, true,
                List.of(new RuleCondition("Checkout", ConditionOp.lte, "26/10/2026")),
                BigDecimal.ONE, Map.of());

        var result = builder.build("rs", 1, List.of(rule), List.of());

        var cond = result.markupRules().get(0).conditions().get(0);
        assertEquals("DATE", cond.kind());
        assertEquals("2026-10-26", String.valueOf(cond.scalarValue()));
    }

    @Test
    void handlesInOperator() {
        var rule = new RuleDefinition("r1", "rs", 0, RuleType.MARKUP, true,
                List.of(new RuleCondition("Broker", ConditionOp.in, List.of("Juniper", "HotelBeds"))),
                BigDecimal.ONE, Map.of());

        var result = builder.build("rs", 1, List.of(rule), List.of());

        var cond = result.markupRules().get(0).conditions().get(0);
        assertEquals("STRING_SET", cond.kind());
        assertEquals(2, cond.setValues().size());
        assertTrue(cond.setValues().contains("juniper"));
        assertTrue(cond.setValues().contains("hotelbeds"));
    }

    @Test
    void handlesEmptyConditionValue() {
        var rule = new RuleDefinition("r1", "rs", 0, RuleType.MARKUP, true,
                List.of(new RuleCondition("Checkin", ConditionOp.lte, "")),
                BigDecimal.ONE, Map.of());

        var result = builder.build("rs", 1, List.of(rule), List.of());

        var cond = result.markupRules().get(0).conditions().get(0);
        assertEquals("STRING", cond.kind());
        assertNull(cond.scalarValue());
    }
}

