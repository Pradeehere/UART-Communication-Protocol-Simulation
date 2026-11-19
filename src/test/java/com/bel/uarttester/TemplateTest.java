package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateTest {
    @TempDir
    Path tempDir;

    private Properties validProps() {
        Properties p = new Properties();
        p.setProperty("port", "COM5");
        p.setProperty("baud", "9600");
        p.setProperty("dataBits", "8");
        p.setProperty("parity", "none");
        p.setProperty("stopBits", "1");
        p.setProperty("send", "0A 1B 2C 3D 4E 5F 60 71");
        p.setProperty("expect", "A0 B1 C2 D3 E4 F5 06 17");
        p.setProperty("verify", "NIBBLE_SWAP");
        p.setProperty("timeoutMs", "1000");
        return p;
    }

    @Test
    void loadsValidFileWithDefaults() throws Exception {
        Path file = tempDir.resolve("my-test.properties");
        Properties p = validProps();
        p.store(Files.newOutputStream(file), null);

        Template t = Template.load(file.toString());
        assertEquals("my-test", t.name);
        assertEquals(8, t.arraySize);
        assertEquals("NIBBLE_SWAP", t.device);
        assertEquals(9600, t.baud);
    }

    @Test
    void parsesHexStrings() {
        assertArrayEquals(new byte[]{0x0A, 0x1B, (byte) 0x2C}, Template.parseHex("0A 1B 2C"));
    }

    @Test
    void rejectsInvalidHex() {
        Properties p = validProps();
        p.setProperty("send", "0A 1FF 2C 3D 4E 5F 60 71");
        assertThrows(IllegalArgumentException.class, () -> new Template(p));

        p.setProperty("send", "0A -1 2C 3D 4E 5F 60 71");
        assertThrows(IllegalArgumentException.class, () -> new Template(p));
    }

    @Test
    void rejectsMissingKeysAndInvalidNumbers() {
        Properties p = validProps();
        p.remove("port");
        assertThrows(IllegalArgumentException.class, () -> new Template(p));

        Properties p2 = validProps();
        p2.setProperty("baud", "abc");
        assertThrows(IllegalArgumentException.class, () -> new Template(p2));

        Properties p3 = validProps();
        p3.setProperty("dataBits", "9");
        assertThrows(IllegalArgumentException.class, () -> new Template(p3));
    }

    @Test
    void rejectsLengthMismatchAndNonMultiples() {
        Properties p = validProps();
        p.setProperty("expect", "A0 B1 C2 D3");
        assertThrows(IllegalArgumentException.class, () -> new Template(p));

        Properties p2 = validProps();
        p2.setProperty("arraySize", "5");
        assertThrows(IllegalArgumentException.class, () -> new Template(p2));
    }

    @Test
    void rejectsUnknownVerifier() {
        Properties p = validProps();
        p.setProperty("verify", "NON_EXISTENT_VERIFIER");
        assertThrows(IllegalArgumentException.class, () -> new Template(p));
    }

    @Test
    void rejectsArraySizeOver255WithFrameLength() {
        Properties p = validProps();
        p.setProperty("arraySize", "256");
        p.setProperty("frameLength", "true");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            if (i > 0) sb.append(' ');
            sb.append("0A");
        }
        p.setProperty("send", sb.toString());
        p.setProperty("expect", sb.toString());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new Template(p));
        assertTrue(ex.getMessage().contains("arraySize"));
        assertTrue(ex.getMessage().contains("frameLength"));
    }
}
