package br.com.cvc.poc.engine.runtime;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RuntimeRegistryTest {

    @Test
    void currentSnapshotReturnsEmptyByDefault() {
        var registry = new RuntimeRegistry();
        var snap = registry.currentSnapshot("rs-1");
        assertNotNull(snap);
        assertEquals(0L, snap.version());
        assertEquals("empty", snap.checksum());
    }

    @Test
    void swapReturnsPreviousAndUpdates() {
        var registry = new RuntimeRegistry();

        var v1 = new RuntimeSnapshot("rs-1", 1L, "c1", Instant.now(),
            List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), Map.of(), null, null);
        var prev = registry.swap("rs-1", v1);
        assertEquals(0L, prev.version()); // was empty

        assertEquals(1L, registry.currentSnapshot("rs-1").version());

        var v2 = new RuntimeSnapshot("rs-1", 2L, "c2", Instant.now(),
            List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), Map.of(), null, null);
        var prev2 = registry.swap("rs-1", v2);
        assertEquals(1L, prev2.version());
        assertEquals(2L, registry.currentSnapshot("rs-1").version());
    }

    @Test
    void differentRulesetsAreIndependent() {
        var registry = new RuntimeRegistry();

        var snapA = new RuntimeSnapshot("rs-A", 3L, "cA", Instant.now(),
            List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), Map.of(), null, null);
        var snapB = new RuntimeSnapshot("rs-B", 7L, "cB", Instant.now(),
            List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), Map.of(), null, null);

        registry.swap("rs-A", snapA);
        registry.swap("rs-B", snapB);

        assertEquals(3L, registry.currentSnapshot("rs-A").version());
        assertEquals(7L, registry.currentSnapshot("rs-B").version());
    }

    @Test
    void backwardCompatCurrentReturnsRulesetRuntime() {
        var registry = new RuntimeRegistry();
        var snap = new RuntimeSnapshot("rs-1", 5L, "c5", Instant.now(),
            List.of("broker"), List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), Map.of(), null, null);
        registry.swap("rs-1", snap);

        var rt = registry.current("rs-1");
        assertEquals(5L, rt.version());
        assertEquals("c5", rt.checksum());
    }
}

