package br.com.cvc.poc.engine.infra.kafka;

import br.com.cvc.poc.contracts.RuleDefinition;
import br.com.cvc.poc.contracts.RulesetUpdateEvent;
import br.com.cvc.poc.engine.runtime.CompiledCondition;
import br.com.cvc.poc.engine.runtime.DroolsCompiler;
import br.com.cvc.poc.engine.runtime.DynamicMatcher;
import br.com.cvc.poc.engine.runtime.IndexBuilder;
import br.com.cvc.poc.engine.runtime.PreparedFieldValue;
import br.com.cvc.poc.engine.runtime.PreparedItem;
import br.com.cvc.poc.engine.runtime.RuleRuntime;
import br.com.cvc.poc.engine.runtime.RulesetRuntime;
import br.com.cvc.poc.engine.runtime.RuntimeRegistry;
import br.com.cvc.poc.normalizer.TextNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RulesetUpdatesConsumer {

  private final RuntimeRegistry registry = new RuntimeRegistry();
  private final Map<String, DroolsCompiler.Compiled> compiled = new ConcurrentHashMap<>();

  @Inject ObjectMapper mapper;

  @ConfigProperty(name = "engine.index.fields")
  Optional<List<String>> preferredIndexFields;

  @Incoming("ruleset_updates")
  public void onMessage(String payload) {
    try {
      var evt = mapper.readValue(payload, RulesetUpdateEvent.class);
      var current = registry.current(evt.rulesetId());

      if (evt.version() < current.version()) return;
      if (evt.version() == current.version() && !Objects.equals(evt.checksum(), current.checksum())) return;
      if (evt.version() == current.version()) return;

      var runtimeRules = evt.payload().rules().stream()
          .map(this::toRuntime)
          .sorted(Comparator.comparingInt(RuleRuntime::peso).thenComparing(RuleRuntime::ruleId))
          .toList();
      var ruleById = runtimeRules.stream().collect(java.util.stream.Collectors.toMap(RuleRuntime::ruleId, r -> r));

      var pref = preferredIndexFields.orElse(List.of("Broker", "Cidade", "Estado", "hotelId"))
          .stream()
          .map(TextNormalizer::normKey)
          .filter(Objects::nonNull)
          .distinct()
          .toList();

      var builtIndex = IndexBuilder.build(runtimeRules, pref);
      var markupRules = runtimeRules.stream().filter(r -> r.ruleType() == br.com.cvc.poc.contracts.RuleType.MARKUP).toList();
      var commissionRules = runtimeRules.stream().filter(r -> r.ruleType() == br.com.cvc.poc.contracts.RuleType.COMMISSION).toList();

      compiled.put(evt.rulesetId(), DroolsCompiler.compile(evt.payload().drl()));

      br.com.cvc.poc.engine.runtime.RuleMatcher.install(evt.rulesetId(), (ruleId, factFields) -> {
        var rule = ruleById.get(ruleId);
        if (rule == null) return false;

        var raw = new HashMap<String, Object>();
        for (var entry : factFields.entrySet()) {
          if (entry.getKey().startsWith("__")) continue;
          var normalizedKey = TextNormalizer.normKey(entry.getKey());
          if (normalizedKey != null) raw.put(normalizedKey, entry.getValue());
        }

        var prepared = new HashMap<String, PreparedFieldValue>();
        for (var entry : raw.entrySet()) {
          prepared.put(entry.getKey(), PreparedFieldValue.from(entry.getValue()));
        }

        var item = new PreparedItem("drools", Map.copyOf(raw), Map.copyOf(prepared), List.of());
        return DynamicMatcher.matchesAll(rule, item);
      });

      registry.swap(evt.rulesetId(), new RulesetRuntime(
          evt.rulesetId(),
          evt.version(),
          evt.checksum(),
          builtIndex.preferredFields(),
          markupRules,
          commissionRules,
          builtIndex.markupIndex(),
          builtIndex.commissionIndex()
      ));

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private RuleRuntime toRuntime(RuleDefinition definition) {
    return new RuleRuntime(
        definition.ruleId(),
        definition.peso(),
        definition.ruleType(),
        definition.enabled(),
        definition.regras(),
        definition.regras().stream().map(CompiledCondition::from).toList(),
        definition.value(),
        definition.metadata()
    );
  }

  public RuntimeRegistry registry() { return registry; }
  public Optional<DroolsCompiler.Compiled> compiled(String rulesetId) { return Optional.ofNullable(compiled.get(rulesetId)); }
}
