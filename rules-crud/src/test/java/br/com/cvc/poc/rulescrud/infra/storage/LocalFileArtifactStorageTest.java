package br.com.cvc.poc.rulescrud.infra.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class LocalFileArtifactStorageTest {

    @TempDir
    Path tempDir;

    LocalFileArtifactStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        storage = new LocalFileArtifactStorage();
        // Inject baseDir via reflection (simulating config property)
        Field baseDirField = LocalFileArtifactStorage.class.getDeclaredField("baseDir");
        baseDirField.setAccessible(true);
        baseDirField.set(storage, tempDir.toString());
        storage.init();
    }

    @Test
    void writeAndReadArtifact() throws IOException {
        storage.writeArtifact("rs-1", 3, "ruleset_v3.drl", "some drl content");
        var result = storage.readArtifact("rs-1", 3, "ruleset_v3.drl");
        assertTrue(result.isPresent());
        assertEquals("some drl content", result.get());
    }

    @Test
    void readNonExistentReturnsEmpty() throws IOException {
        var result = storage.readArtifact("rs-1", 99, "nope.drl");
        assertTrue(result.isEmpty());
    }

    @Test
    void existsReturnsTrueForExistingFile() throws IOException {
        storage.writeArtifact("rs-1", 1, "test.json", "{}");
        assertTrue(storage.exists("rs-1", 1, "test.json"));
        assertFalse(storage.exists("rs-1", 1, "other.json"));
    }

    @Test
    void ensureDirectoryCreatesNestedFolders() throws IOException {
        storage.ensureDirectory("my-ruleset", 7);
        Path expected = tempDir.resolve("my-ruleset").resolve("v7");
        assertTrue(Files.isDirectory(expected));
    }

    @Test
    void resolveManifestPathFormat() {
        var path = storage.resolveManifestPath("abc-123", 5);
        assertEquals("abc-123/v5/ruleset_v5.manifest.json", path);
    }

    @Test
    void readByPathWorks() throws IOException {
        storage.writeArtifact("rs-1", 2, "data.json", "{\"key\":\"val\"}");
        var result = storage.readByPath("rs-1/v2/data.json");
        assertTrue(result.isPresent());
        assertEquals("{\"key\":\"val\"}", result.get());
    }

    @Test
    void multipleVersionsDontInterfere() throws IOException {
        storage.writeArtifact("rs-1", 1, "ruleset_v1.drl", "v1 content");
        storage.writeArtifact("rs-1", 2, "ruleset_v2.drl", "v2 content");

        assertEquals("v1 content", storage.readArtifact("rs-1", 1, "ruleset_v1.drl").get());
        assertEquals("v2 content", storage.readArtifact("rs-1", 2, "ruleset_v2.drl").get());
    }
}

