package br.com.cvc.poc.rulescrud.infra.storage;

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

@ApplicationScoped
public class LocalFileArtifactStorage implements ArtifactStorageService {

    @ConfigProperty(name = "ruleset.storage.base-dir", defaultValue = "./storage")
    String baseDir;

    private Path root;

    @PostConstruct
    void init() {
        root = Path.of(baseDir).toAbsolutePath().normalize();
        Log.infof("Artifact storage base-dir: %s", root);
        try {
            Files.createDirectories(root);
            // Validate writable
            var probe = root.resolve(".write-probe");
            Files.writeString(probe, "ok", StandardCharsets.UTF_8);
            Files.deleteIfExists(probe);
            Log.infof("Artifact storage directory validated: writable");
        } catch (IOException e) {
            throw new RuntimeException("Artifact storage directory is not writable: " + root, e);
        }
    }

    @Override
    public void writeArtifact(String rulesetId, long version, String fileName, String content) throws IOException {
        ensureDirectory(rulesetId, version);
        Path file = resolvePath(rulesetId, version, fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        Log.infof("Artifact written: %s", file);
    }

    @Override
    public Optional<String> readArtifact(String rulesetId, long version, String fileName) throws IOException {
        Path file = resolvePath(rulesetId, version, fileName);
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
        Path dir = root.resolve(rulesetId).resolve("v" + version);
        Files.createDirectories(dir);
    }

    @Override
    public String resolveManifestPath(String rulesetId, long version) {
        return rulesetId + "/v" + version + "/ruleset_v" + version + ".manifest.json";
    }

    @Override
    public boolean exists(String rulesetId, long version, String fileName) {
        return Files.exists(resolvePath(rulesetId, version, fileName));
    }

    private Path resolvePath(String rulesetId, long version, String fileName) {
        return root.resolve(rulesetId).resolve("v" + version).resolve(fileName);
    }
}

