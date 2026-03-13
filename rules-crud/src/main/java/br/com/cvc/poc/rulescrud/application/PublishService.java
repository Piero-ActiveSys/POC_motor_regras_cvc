package br.com.cvc.poc.rulescrud.application;

import br.com.cvc.poc.contracts.*;
import br.com.cvc.poc.rulescrud.infra.db.OutboxEventEntity;
import br.com.cvc.poc.rulescrud.infra.db.RuleEntity;
import br.com.cvc.poc.rulescrud.infra.db.RulesetVersionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class PublishService {

    @Inject RuleCanonicalizer canonicalizer;
    @Inject ObjectMapper mapper;
    @Inject ArtifactStorageService storage;
    @Inject RuntimeJsonBuilder runtimeJsonBuilder;

    @ConfigProperty(name = "rules.normalization.enabled", defaultValue = "true")
    boolean normalizationEnabled;

    @ConfigProperty(name = "engine.index.fields", defaultValue = "Broker,Cidade,Estado,hotelId")
    Optional<List<String>> preferredIndexFields;

    @Transactional
    public PublishResponse publish(UUID rulesetId, String publishedBy) {
        Log.infof("Publishing ruleset %s by %s (normalization=%s)", rulesetId, publishedBy, normalizationEnabled);

        // 1. Fetch rules and build canonical definitions
        List<RuleEntity> entities = RuleEntity.list("rulesetId", rulesetId);
        List<RuleDefinition> defs = entities.stream()
                .map(e -> canonicalizer.toDefinition(e, normalizationEnabled))
                .sorted(Comparator.comparingInt(RuleDefinition::peso).thenComparing(d -> d.ruleType().name()))
                .collect(Collectors.toList());

        // 2. Generate DRL
        String drl = DrlBuilder.build(defs);

        // 3. Generate canonical JSON
        String canonicalJson;
        try { canonicalJson = mapper.writeValueAsString(defs); }
        catch (Exception e) { throw new RuntimeException("Failed to serialize canonical JSON", e); }

        // 4. Generate runtime.json
        long nextVersion = resolveNextVersion(rulesetId);
        var indexFields = preferredIndexFields.orElse(List.of("Broker", "Cidade", "Estado", "hotelId"));
        var runtimeDto = runtimeJsonBuilder.build(rulesetId.toString(), nextVersion, defs, indexFields);
        String runtimeJson;
        try { runtimeJson = mapper.writeValueAsString(runtimeDto); }
        catch (Exception e) { throw new RuntimeException("Failed to serialize runtime JSON", e); }

        // 5. Compute checksum over the bundle
        String checksum = Checksum.sha256(drl + "|" + canonicalJson + "|" + runtimeJson);

        // 6. Write artifacts to storage
        String drlFile = "ruleset_v" + nextVersion + ".drl";
        String canonicalFile = "ruleset_v" + nextVersion + ".canonical.json";
        String runtimeFile = "ruleset_v" + nextVersion + ".runtime.json";
        String manifestFile = "ruleset_v" + nextVersion + ".manifest.json";

        try {
            storage.ensureDirectory(rulesetId.toString(), nextVersion);
            storage.writeArtifact(rulesetId.toString(), nextVersion, drlFile, drl);
            storage.writeArtifact(rulesetId.toString(), nextVersion, canonicalFile, canonicalJson);
            storage.writeArtifact(rulesetId.toString(), nextVersion, runtimeFile, runtimeJson);
            Log.infof("Artifacts written for ruleset %s v%d", rulesetId, nextVersion);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write artifacts for ruleset " + rulesetId + " v" + nextVersion, e);
        }

        // 7. Generate and write manifest
        String generatedAt = OffsetDateTime.now().toString();
        var manifest = new ManifestDto(
                rulesetId.toString(),
                nextVersion,
                RulesetEventType.RULESET_PUBLISHED.name(),
                generatedAt,
                normalizationEnabled,
                checksum,
                Map.of(
                        ManifestDto.FILE_DRL, drlFile,
                        ManifestDto.FILE_CANONICAL, canonicalFile,
                        ManifestDto.FILE_RUNTIME, runtimeFile
                )
        );
        try {
            String manifestJson = mapper.writeValueAsString(manifest);
            storage.writeArtifact(rulesetId.toString(), nextVersion, manifestFile, manifestJson);
            Log.infof("Manifest written for ruleset %s v%d", rulesetId, nextVersion);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write manifest for ruleset " + rulesetId + " v" + nextVersion, e);
        }

        // 8. Pre-publish validation: verify all artifacts exist
        validateArtifactsExist(rulesetId.toString(), nextVersion, drlFile, canonicalFile, runtimeFile, manifestFile);

        // 9. Persist version entity
        String manifestPath = storage.resolveManifestPath(rulesetId.toString(), nextVersion);
        var ver = RulesetVersionEntity.create(rulesetId, nextVersion, checksum, drl, canonicalJson, manifestPath);
        ver.persist();
        Log.infof("Version %d persisted for ruleset %s (checksum=%s)", nextVersion, rulesetId, checksum);

        // 10. Create lightweight Kafka outbox event
        var evt = new RulesetEvent(
                RulesetEvent.CURRENT_SCHEMA_VERSION,
                RulesetEventType.RULESET_PUBLISHED,
                rulesetId.toString(),
                nextVersion,
                checksum,
                generatedAt,
                manifestPath
        );

        String eventPayload;
        try { eventPayload = mapper.writeValueAsString(evt); }
        catch (Exception e) { throw new RuntimeException("Failed to serialize event", e); }

        var out = OutboxEventEntity.create(rulesetId, "RULESET_PUBLISHED", eventPayload,
                publishedBy, "New version " + nextVersion + " published");
        out.persist();
        Log.infof("Outbox event created for ruleset %s v%d", rulesetId, nextVersion);

        return new PublishResponse(
                rulesetId.toString(),
                nextVersion,
                RulesetEventType.RULESET_PUBLISHED.name(),
                generatedAt,
                checksum,
                manifestPath
        );
    }

    /**
     * Re-publish an existing version (for rollback / version activation).
     */
    @Transactional
    public PublishResponse activateVersion(UUID rulesetId, long version, String requestedBy) {
        Log.infof("Activating version %d of ruleset %s by %s", version, rulesetId, requestedBy);

        var existing = RulesetVersionEntity.find("rulesetId = ?1 and version = ?2", rulesetId, version)
                .firstResultOptional()
                .orElseThrow(() -> new RuntimeException("Version " + version + " not found for ruleset " + rulesetId));
        var ver = (RulesetVersionEntity) existing;

        // Validate artifacts still exist on storage
        String manifestPath = ver.manifestPath;
        if (manifestPath == null || manifestPath.isBlank()) {
            throw new RuntimeException("No manifest path for version " + version + " of ruleset " + rulesetId);
        }

        try {
            var manifestContent = storage.readByPath(manifestPath);
            if (manifestContent.isEmpty()) {
                throw new RuntimeException("Manifest file not found at " + manifestPath);
            }
            var manifest = mapper.readValue(manifestContent.get(), ManifestDto.class);

            // Validate referenced files exist
            for (var entry : manifest.files().entrySet()) {
                if (!storage.exists(rulesetId.toString(), version, entry.getValue())) {
                    throw new RuntimeException("Artifact missing: " + entry.getValue());
                }
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Failed to validate artifacts for rollback", e);
        }

        String generatedAt = OffsetDateTime.now().toString();
        var evt = new RulesetEvent(
                RulesetEvent.CURRENT_SCHEMA_VERSION,
                RulesetEventType.RULESET_VERSION_ACTIVATED,
                rulesetId.toString(),
                version,
                ver.checksum,
                generatedAt,
                manifestPath
        );

        String eventPayload;
        try { eventPayload = mapper.writeValueAsString(evt); }
        catch (Exception e) { throw new RuntimeException("Failed to serialize event", e); }

        var out = OutboxEventEntity.create(rulesetId, "RULESET_VERSION_ACTIVATED", eventPayload,
                requestedBy, "Rollback/reactivation of version " + version);
        out.persist();
        Log.infof("Rollback/activation event created for ruleset %s v%d", rulesetId, version);

        return new PublishResponse(
                rulesetId.toString(),
                version,
                RulesetEventType.RULESET_VERSION_ACTIVATED.name(),
                generatedAt,
                ver.checksum,
                manifestPath
        );
    }

    private long resolveNextVersion(UUID rulesetId) {
        Long last = RulesetVersionEntity.find("rulesetId = ?1 order by version desc", rulesetId)
                .firstResultOptional().map(x -> ((RulesetVersionEntity) x).version).orElse(0L);
        return last + 1;
    }

    private void validateArtifactsExist(String rulesetId, long version, String... fileNames) {
        for (var fileName : fileNames) {
            if (!storage.exists(rulesetId, version, fileName)) {
                throw new RuntimeException("Pre-publish validation failed: artifact missing: " + fileName);
            }
        }
        Log.infof("Pre-publish validation passed for ruleset %s v%d (%d artifacts)", rulesetId, version, fileNames.length);
    }
}
