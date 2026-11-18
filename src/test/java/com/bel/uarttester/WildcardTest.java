package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WildcardTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesExpectPatternWithWildcardsAndRejectsInvalid() {
        int[] pattern = Template.parseExpectPattern("10 ?? 30 ??", "expect", "test.properties");
        assertEquals(4, pattern.length);
        assertEquals(0x10, pattern[0]);
        assertEquals(-1, pattern[1]);
        assertEquals(0x30, pattern[2]);
        assertEquals(-1, pattern[3]);

        // Reject invalid tokens
        assertThrows(IllegalArgumentException.class, () ->
                Template.parseExpectPattern("10 ? 30", "expect", "test.properties"));
        assertThrows(IllegalArgumentException.class, () ->
                Template.parseExpectPattern("10 1FF 30", "expect", "test.properties"));
    }

    @Test
    void exactMatchVerifierSupportsWildcards() {
        ExactMatchVerifier verifier = new ExactMatchVerifier();
        int[] pattern = new int[]{0x11, -1, 0x31, 0x41};

        // Any value at index 1 should match
        assertTrue(verifier.verifyPattern(new byte[]{0x11, 0x00, 0x31, 0x41}, pattern, 0, 4));
        assertTrue(verifier.verifyPattern(new byte[]{0x11, 0x22, 0x31, 0x41}, pattern, 0, 4));
        assertTrue(verifier.verifyPattern(new byte[]{0x11, (byte) 0xFF, 0x31, 0x41}, pattern, 0, 4));

        // Mismatches on concrete bytes fail
        assertFalse(verifier.verifyPattern(new byte[]{0x12, 0x22, 0x31, 0x41}, pattern, 0, 4));
        assertFalse(verifier.verifyPattern(new byte[]{0x11, 0x22, 0x32, 0x41}, pattern, 0, 4));
    }

    @Test
    void runsEndToEndWithWildcardTemplate() throws Exception {
        Path p = tempDir.resolve("wildcard.properties");
        Properties props = new Properties();
        props.setProperty("port", "COM5");
        props.setProperty("baud", "9600");
        props.setProperty("dataBits", "8");
        props.setProperty("parity", "none");
        props.setProperty("stopBits", "1");
        props.setProperty("arraySize", "4");
        props.setProperty("send", "10 20 30 40");
        props.setProperty("expect", "11 ?? 31 41");
        props.setProperty("verify", "EXACT_MATCH");
        props.setProperty("device", "ADD:01");
        props.setProperty("timeoutMs", "1000");
        props.store(Files.newOutputStream(p), null);

        Template t = Template.load(p.toString());
        assertTrue(Main.run(t, true, tempDir.toString()));
    }
}
