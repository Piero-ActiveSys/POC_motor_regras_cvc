package br.com.cvc.poc.engine.runtime;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RuntimeSnapshotTest {

    @Test
    void emptySnapshotHasDefaults() {
        var snap = RuntimeSnapshot.empty("rs-1");
        assertEquals("rs-1", snap.rulesetId());
        assertEquals(0L, snap.version());
        assertEquals("empty", snap.checksum());
        assertTrue(snap.markupRules().isEmpty());
        assertTrue(snap.commissionRules().isEmpty());
        assertNull(snap.compiledDrl());
        assertNull(snap.manifest());
    }

    @Test
    void toRulesetRuntimePreservesFields() {
        var snap = new RuntimeSnapshot(
            "rs-1", 5L, "sha256:abc", Instant.now(),
            List.of("broker", "cidade"),
            List.of(), List.of(),
            Map.of(), Map.of(), List.of(), List.of(), Map.of(),
            null, null, FieldTypeRegistry.empty()
        );
        var rt = snap.toRulesetRuntime();
        assertEquals("rs-1", rt.rulesetId());
        assertEquals(5L, rt.version());
        assertEquals("sha256:abc", rt.checksum());
        assertEquals(List.of("broker", "cidade"), rt.preferredIndexFields());
    }
}

