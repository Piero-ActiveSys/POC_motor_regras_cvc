package br.com.cvc.poc.rulescrud.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ChecksumTest {

    @Test
    void sha256ProducesDeterministicHash() {
        var hash1 = Checksum.sha256("hello world");
        var hash2 = Checksum.sha256("hello world");
        assertEquals(hash1, hash2);
        assertTrue(hash1.startsWith("sha256:"));
    }

    @Test
    void differentInputProducesDifferentHash() {
        var hash1 = Checksum.sha256("content-a");
        var hash2 = Checksum.sha256("content-b");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void checksumChangesWhenAnyPartChanges() {
        var drl = "rule content";
        var canonical = "{\"rules\":[]}";
        var runtime = "{\"markupRules\":[]}";

        var hash1 = Checksum.sha256(drl + "|" + canonical + "|" + runtime);
        var hash2 = Checksum.sha256(drl + "|" + canonical + "|" + runtime + "x");
        assertNotEquals(hash1, hash2);
    }
}