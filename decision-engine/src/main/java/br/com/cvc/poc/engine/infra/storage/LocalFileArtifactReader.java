package br.com.cvc.poc.engine.infra.storage;

import br.com.cvc.poc.contracts.ArtifactStorageService;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Read-only artifact storage for the decision-engine.
 * Reads from the same shared directory that rules-crud writes to.
 */
@ApplicationScoped
public class LocalFileArtifactReader implements ArtifactStorageService {

    @ConfigProperty(name = "ruleset.storage.base-dir", defaultValue = "./storage")
    String baseDir;

    private Path root;

    @PostConstruct
    void init() {
        root = Path.of(baseDir).toAbsolutePath().normalize();
        Log.infof("Decision-engine artifact storage base-dir: %s", root);
        if (!Files.isDirectory(root)) {
            Log.warnf("Artifact storage directory does not exist yet: %s (will be created by rules-crud)", root);
        } else {
            Log.infof("Artifact storage directory accessible: %s", root);
        }
    }

    @Override
    public void writeArtifact(String rulesetId, long version, String fileName, String content) throws IOException {
        throw new UnsupportedOperationException("Decision-engine does not write artifacts");
    }

    @Override
    public Optional<String> readArtifact(String rulesetId, long version, String fileName) throws IOException {
        Path file = root.resolve(rulesetId).resolve("v" + version).resolve(fileName);
        if (!Files.exists(file)) return Optional.empty();
        return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    }

    @Override
    public Optional<String> readByPath(String relativePath) throws IOException {
        Path file = root.resolve(relativePath).normalize();
        if (!Files.exists(file)) return Optional.empty();
        return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    }

    @Override
    public void ensureDirectory(String rulesetId, long version) throws IOException {
        // no-op for reader
    }

    @Override
    public String resolveManifestPath(String rulesetId, long version) {
        return rulesetId + "/v" + version + "/ruleset_v" + version + ".manifest.json";
    }

    @Override
    public boolean exists(String rulesetId, long version, String fileName) {
        return Files.exists(root.resolve(rulesetId).resolve("v" + version).resolve(fileName));
    }
}

