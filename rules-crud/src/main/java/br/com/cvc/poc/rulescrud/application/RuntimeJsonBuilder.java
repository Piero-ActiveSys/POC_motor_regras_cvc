package br.com.cvc.poc.rulescrud.application;

import br.com.cvc.poc.contracts.ConditionOp;
import br.com.cvc.poc.contracts.RuleCondition;
import br.com.cvc.poc.contracts.RuleDefinition;
import br.com.cvc.poc.contracts.RuleType;
import br.com.cvc.poc.contracts.RuntimeDto;
import br.com.cvc.poc.contracts.RuntimeDto.RuntimeConditionDto;
import br.com.cvc.poc.contracts.RuntimeDto.RuntimeRuleDto;
import br.com.cvc.poc.normalizer.DateNormalizer;
import br.com.cvc.poc.normalizer.TextNormalizer;
import br.com.cvc.poc.normalizer.ValueCoercion;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the pre-processed runtime.json content ready for consumption by the decision-engine.
 * This moves heavy processing from the consumer to the producer.
 */
@ApplicationScoped
public class RuntimeJsonBuilder {

    private static final List<String> DEFAULT_INDEX_FIELDS = List.of("Broker", "Cidade", "Estado", "hotelId");

    public RuntimeDto build(String rulesetId, long version, List<RuleDefinition> sortedDefs, List<String> preferredIndexFields) {
        var indexFields = preferredIndexFields != null && !preferredIndexFields.isEmpty()
                ? preferredIndexFields : DEFAULT_INDEX_FIELDS;

        List<String> normalizedIndexFields = indexFields.stream()
                .map(TextNormalizer::normKey)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        var markupRules = new ArrayList<RuntimeRuleDto>();
        var commissionRules = new ArrayList<RuntimeRuleDto>();

        for (var def : sortedDefs) {
            var ruleDto = toRuntimeRule(def);
            if (def.ruleType() == RuleType.MARKUP) {
                markupRules.add(ruleDto);
            } else {
                commissionRules.add(ruleDto);
            }
        }

        return new RuntimeDto(rulesetId, version, normalizedIndexFields, markupRules, commissionRules);
    }

    private RuntimeRuleDto toRuntimeRule(RuleDefinition def) {
        List<RuntimeConditionDto> conditions = def.regras() == null ? List.of()
                : def.regras().stream().map(this::toRuntimeCondition).collect(Collectors.toList());

        return new RuntimeRuleDto(
                def.ruleId(),
                def.peso(),
                def.ruleType().name(),
                def.enabled(),
                conditions,
                def.value() != null ? def.value().toPlainString() : "0",
                def.metadata()
        );
    }

    private RuntimeConditionDto toRuntimeCondition(RuleCondition condition) {
        var field = TextNormalizer.normKey(condition.campo());
        if (field == null || condition.operacao() == null) {
            return new RuntimeConditionDto(field, opName(condition.operacao()), "STRING", null, List.of());
        }

        Object raw = condition.valor();
        if (isEmpty(raw)) {
            return new RuntimeConditionDto(field, opName(condition.operacao()), "STRING", null, List.of());
        }

        if (condition.operacao() == ConditionOp.in) {
            var vals = new ArrayList<String>();
            for (var item : ValueCoercion.toStringList(raw)) {
                var normalized = TextNormalizer.normValue(item);
                if (normalized != null && !normalized.isBlank()) vals.add(normalized);
            }
            return new RuntimeConditionDto(field, opName(condition.operacao()), "STRING_SET", null, vals);
        }

        LocalDate date = tryParseDate(raw);
        if (date != null) {
            return new RuntimeConditionDto(field, opName(condition.operacao()), "DATE", date.toString(), List.of());
        }

        Double number = tryParseNumber(raw);
        if (number != null) {
            return new RuntimeConditionDto(field, opName(condition.operacao()), "NUMBER", number, List.of());
        }

        Boolean bool = ValueCoercion.toBoolean(raw);
        if (bool != null) {
            return new RuntimeConditionDto(field, opName(condition.operacao()), "BOOLEAN", bool, List.of());
        }

        return new RuntimeConditionDto(field, opName(condition.operacao()), "STRING", TextNormalizer.normValue(raw), List.of());
    }

    private static String opName(ConditionOp op) {
        return op != null ? op.name() : null;
    }

    private static boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.trim().isEmpty();
        if (value instanceof List<?> list) return list.isEmpty();
        return false;
    }

    private static LocalDate tryParseDate(Object v) {
        try { return DateNormalizer.parseBr(v); }
        catch (Exception e) { return null; }
    }

    private static Double tryParseNumber(Object v) {
        try { return ValueCoercion.toDouble(v); }
        catch (Exception e) { return null; }
    }
}

