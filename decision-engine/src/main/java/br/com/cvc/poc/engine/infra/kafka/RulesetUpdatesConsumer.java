package br.com.cvc.poc.engine.infra.kafka;

import br.com.cvc.poc.contracts.*;
import br.com.cvc.poc.engine.runtime.*;
import br.com.cvc.poc.normalizer.TextNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@ApplicationScoped
public class RulesetUpdatesConsumer {

  private final RuntimeRegistry registry = new RuntimeRegistry();
  private final Map<String, DroolsCompiler.Compiled> compiled = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  @Inject ObjectMapper mapper;
  @Inject ArtifactStorageService storage;

  @ConfigProperty(name = "engine.index.fields")
  Optional<List<String>> preferredIndexFields;

  @ConfigProperty(name = "engine.drl.compile-on-activation", defaultValue = "false")
  boolean compileOnActivation;

  @Incoming("ruleset_updates")
  public void onMessage(String payload) {
    RulesetEvent evt;
    try {
      evt = mapper.readValue(payload, RulesetEvent.class);
    } catch (Exception e) {
      // Try legacy format for backward compat
      try {
        var legacy = mapper.readValue(payload, RulesetUpdateEvent.class);
        handleLegacyEvent(legacy);
        return;
      } catch (Exception ex) {
        Log.errorf("Failed to deserialize event: %s", e.getMessage());
        return;
      }
    }

    var rulesetId = evt.rulesetId();
    var lock = locks.computeIfAbsent(rulesetId, __ -> new ReentrantLock());
    lock.lock();
    try {
      processEvent(evt);
    } catch (Exception e) {
      Log.errorf(e, "Activation failed for ruleset %s v%d: %s", rulesetId, evt.version(), e.getMessage());
    } finally {
      lock.unlock();
    }
  }

  private void processEvent(RulesetEvent evt) {
    var current = registry.currentSnapshot(evt.rulesetId());

    // --- Version acceptance policy ---
    if (evt.eventType() == RulesetEventType.RULESET_PUBLISHED) {
      if (evt.version() <= current.version()) {
        Log.infof("Ignoring event: version %d <= current %d for ruleset %s", evt.version(), current.version(), evt.rulesetId());
        return;
      }
    } else if (evt.eventType() == RulesetEventType.RULESET_VERSION_ACTIVATED) {
      // rollback/reactivation: any version is accepted — no filtering needed
      Log.infof("Accepting rollback/reactivation event for ruleset %s v%d", evt.rulesetId(), evt.version());
    } else {
      Log.warnf("Unknown event type: %s", evt.eventType());
      return;
    }

    // --- Idempotency check ---
    if (evt.version() == current.version() && Objects.equals(evt.checksum(), current.checksum())) {
      Log.infof("Idempotent: version %d already active for ruleset %s", evt.version(), evt.rulesetId());
      return;
    }

    // --- Validate schema version ---
    if (evt.schemaVersion() > RulesetEvent.CURRENT_SCHEMA_VERSION) {
      Log.errorf("Unsupported schema version %d (max supported: %d)", evt.schemaVersion(), RulesetEvent.CURRENT_SCHEMA_VERSION);
      return;
    }

    Log.infof("Processing %s event for ruleset %s v%d (checksum=%s)", evt.eventType(), evt.rulesetId(), evt.version(), evt.checksum());

    // === STAGE ===
    ManifestDto manifest;
    String runtimeJsonContent;
    String drlContent;
    try {
      var manifestOpt = storage.readByPath(evt.manifestPath());
      if (manifestOpt.isEmpty()) {
        Log.errorf("Manifest not found at path: %s", evt.manifestPath());
        return;
      }
      manifest = mapper.readValue(manifestOpt.get(), ManifestDto.class);
      Log.infof("Manifest loaded for ruleset %s v%d", evt.rulesetId(), evt.version());

      // Load runtime.json
      var runtimeFile = manifest.files().get(ManifestDto.FILE_RUNTIME);
      var runtimeOpt = storage.readArtifact(evt.rulesetId(), evt.version(), runtimeFile);
      if (runtimeOpt.isEmpty()) {
        Log.errorf("Runtime file not found: %s", runtimeFile);
        return;
      }
      runtimeJsonContent = runtimeOpt.get();

      // Load DRL (optional based on flag)
      drlContent = null;
      if (compileOnActivation) {
        var drlFile = manifest.files().get(ManifestDto.FILE_DRL);
        var drlOpt = storage.readArtifact(evt.rulesetId(), evt.version(), drlFile);
        if (drlOpt.isEmpty()) {
          Log.warnf("DRL file not found: %s (compilation will be skipped)", drlFile);
        } else {
          drlContent = drlOpt.get();
        }
      }
    } catch (Exception e) {
      Log.errorf(e, "Stage failed for ruleset %s v%d", evt.rulesetId(), evt.version());
      return;
    }

    // === VALIDATE ===
    if (!Objects.equals(manifest.checksum(), evt.checksum())) {
      Log.errorf("Checksum mismatch: manifest=%s, event=%s — rejecting activation", manifest.checksum(), evt.checksum());
      return;
    }

    // Parse runtime.json
    RuntimeDto runtimeDto;
    try {
      runtimeDto = mapper.readValue(runtimeJsonContent, RuntimeDto.class);
    } catch (Exception e) {
      Log.errorf(e, "Failed to parse runtime.json for ruleset %s v%d", evt.rulesetId(), evt.version());
      return;
    }

    // Build runtime rules from pre-processed DTO
    List<RuleRuntime> markupRules = runtimeDto.markupRules().stream().map(this::fromDto).collect(Collectors.toList());
    List<RuleRuntime> commissionRules = runtimeDto.commissionRules().stream().map(this::fromDto).collect(Collectors.toList());
    var allRules = new ArrayList<RuleRuntime>(markupRules.size() + commissionRules.size());
    allRules.addAll(markupRules);
    allRules.addAll(commissionRules);
    Map<String, RuleRuntime> ruleById = allRules.stream().collect(Collectors.toMap(RuleRuntime::ruleId, r -> r));

    // Build indices
    var pref = runtimeDto.preferredIndexFields() != null ? runtimeDto.preferredIndexFields() : List.<String>of();
    var builtIndex = IndexBuilder.build(allRules, pref);

    // Optional DRL compilation
    DroolsCompiler.Compiled compiledDrl = null;
    if (compileOnActivation && drlContent != null) {
      try {
        compiledDrl = DroolsCompiler.compile(drlContent);
        compiled.put(evt.rulesetId(), compiledDrl);
        Log.infof("DRL compiled for ruleset %s v%d", evt.rulesetId(), evt.version());

        // Install matcher for Drools bridge
        final var finalRuleById = ruleById;
        RuleMatcher.install(evt.rulesetId(), (ruleId, factFields) -> {
          var rule = finalRuleById.get(ruleId);
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
      } catch (Exception e) {
        Log.errorf(e, "DRL compilation failed for ruleset %s v%d (continuing without DRL)", evt.rulesetId(), evt.version());
      }
    }

    // === SWAP ===
    var typeRegistry = FieldTypeRegistry.build(allRules);
    var snapshot = new RuntimeSnapshot(
        evt.rulesetId(),
        evt.version(),
        evt.checksum(),
        Instant.now(),
        builtIndex.preferredFields(),
        markupRules,
        commissionRules,
        builtIndex.markupIndex(),
        builtIndex.commissionIndex(),
        builtIndex.markupCatchAll(),
        builtIndex.commissionCatchAll(),
        ruleById,
        compiledDrl,
        manifest,
        typeRegistry
    );

    var previous = registry.swap(evt.rulesetId(), snapshot);
    Log.infof("Swap completed: ruleset %s v%d -> v%d (checksum=%s, fieldTypeRegistry=%d fields)",
        evt.rulesetId(), previous.version(), evt.version(), evt.checksum(), typeRegistry.size());
  }

  /**
   * Handle legacy RulesetUpdateEvent format (backward compat during migration).
   */
  private void handleLegacyEvent(RulesetUpdateEvent legacyEvt) {
    Log.warnf("Processing LEGACY event for ruleset %s v%d — consider migrating to new event format",
        legacyEvt.rulesetId(), legacyEvt.version());

    var rulesetId = legacyEvt.rulesetId();
    var lock = locks.computeIfAbsent(rulesetId, __ -> new ReentrantLock());
    lock.lock();
    try {
      var current = registry.currentSnapshot(rulesetId);
      if (legacyEvt.version() <= current.version()) return;

      List<RuleRuntime> runtimeRules = legacyEvt.payload().rules().stream()
          .map(this::legacyToRuntime)
          .sorted(Comparator.comparingInt(RuleRuntime::peso).thenComparing(RuleRuntime::ruleId))
          .collect(Collectors.toList());
      Map<String, RuleRuntime> ruleById = runtimeRules.stream().collect(Collectors.toMap(RuleRuntime::ruleId, r -> r));

      List<String> pref = preferredIndexFields.orElse(List.of("Broker", "Cidade", "Estado", "hotelId"))
          .stream().map(TextNormalizer::normKey).filter(Objects::nonNull).distinct().collect(Collectors.toList());

      var builtIndex = IndexBuilder.build(runtimeRules, pref);
      List<RuleRuntime> markupRules = runtimeRules.stream().filter(r -> r.ruleType() == RuleType.MARKUP).collect(Collectors.toList());
      List<RuleRuntime> commissionRules = runtimeRules.stream().filter(r -> r.ruleType() == RuleType.COMMISSION).collect(Collectors.toList());

      DroolsCompiler.Compiled compiledDrl = null;
      if (compileOnActivation) {
        try {
          compiledDrl = DroolsCompiler.compile(legacyEvt.payload().drl());
          compiled.put(rulesetId, compiledDrl);
        } catch (Exception e) {
          Log.errorf(e, "Legacy DRL compilation failed");
        }
      }

      var legacyTypeRegistry = FieldTypeRegistry.build(runtimeRules);
      var snapshot = new RuntimeSnapshot(
          rulesetId, legacyEvt.version(), legacyEvt.checksum(), Instant.now(),
          builtIndex.preferredFields(), markupRules, commissionRules,
          builtIndex.markupIndex(), builtIndex.commissionIndex(),
          builtIndex.markupCatchAll(), builtIndex.commissionCatchAll(),
          ruleById, compiledDrl, null, legacyTypeRegistry
      );
      registry.swap(rulesetId, snapshot);
      Log.infof("Legacy swap completed: ruleset %s v%d", rulesetId, legacyEvt.version());
    } finally {
      lock.unlock();
    }
  }

  private RuleRuntime fromDto(RuntimeDto.RuntimeRuleDto dto) {
    List<RuleCondition> conditions = dto.conditions() == null ? List.of()
        : dto.conditions().stream().map(c -> new RuleCondition(c.field(), safeOp(c.op()), c.scalarValue())).collect(Collectors.toList());
    List<CompiledCondition> compiledConditions = dto.conditions() == null ? List.of()
        : dto.conditions().stream().map(this::fromConditionDto).collect(Collectors.toList());

    return new RuleRuntime(
        dto.ruleId(), dto.peso(),
        RuleType.valueOf(dto.ruleType()), dto.enabled(),
        conditions, compiledConditions,
        dto.value() != null ? new BigDecimal(dto.value()) : BigDecimal.ZERO,
        dto.metadata()
    );
  }

  private CompiledCondition fromConditionDto(RuntimeDto.RuntimeConditionDto dto) {
    var kind = CompiledCondition.ValueKind.valueOf(dto.kind());
    var op = safeOp(dto.op());
    Object scalar = null;
    if (dto.scalarValue() != null) {
      scalar = switch (kind) {
        case DATE -> java.time.LocalDate.parse(String.valueOf(dto.scalarValue()));
        case NUMBER -> dto.scalarValue() instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(dto.scalarValue()));
        case BOOLEAN -> dto.scalarValue() instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(dto.scalarValue()));
        case STRING -> String.valueOf(dto.scalarValue());
        case STRING_SET -> null;
      };
    }
    var setValues = dto.setValues() != null ? new java.util.LinkedHashSet<>(dto.setValues()) : Set.<String>of();
    return new CompiledCondition(dto.field(), op, kind, scalar, setValues);
  }

  private RuleRuntime legacyToRuntime(RuleDefinition definition) {
    return new RuleRuntime(
        definition.ruleId(), definition.peso(), definition.ruleType(), definition.enabled(),
        definition.regras(),
        definition.regras().stream().map(CompiledCondition::from).collect(Collectors.toList()),
        definition.value(), definition.metadata()
    );
  }

  private static ConditionOp safeOp(String op) {
    if (op == null) return null;
    try { return ConditionOp.valueOf(op); }
    catch (Exception e) { return null; }
  }

  public RuntimeRegistry registry() { return registry; }
  public Optional<DroolsCompiler.Compiled> compiled(String rulesetId) { return Optional.ofNullable(compiled.get(rulesetId)); }
}
