package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FaultInjectionTest {
    @TempDir
    Path tempDir;

    private Path createTemplate(String name, String extraKey, String extraVal, int timeoutMs) throws Exception {
        Path p = tempDir.resolve(name);
        Properties props = new Properties();
        props.setProperty("port", "COM5");
        props.setProperty("baud", "9600");
        props.setProperty("dataBits", "8");
        props.setProperty("parity", "none");
        props.setProperty("stopBits", "1");
        props.setProperty("arraySize", "4");
        props.setProperty("send", "0A 1B 2C 3D");
        props.setProperty("expect", "A0 B1 C2 D3");
        props.setProperty("verify", "NIBBLE_SWAP");
        props.setProperty("device", "NIBBLE_SWAP");
        props.setProperty("timeoutMs", String.valueOf(timeoutMs));
        if (extraKey != null && extraVal != null) {
            props.setProperty(extraKey, extraVal);
        }
        props.store(Files.newOutputStream(p), null);
        return p;
    }

    @Test
    void faultDropCausesTimeoutFailure() throws Exception {
        Path p = createTemplate("drop.properties", "faultDrop", "true", 100);
        Template t = Template.load(p.toString());
        assertFalse(Main.run(t, true, tempDir.toString()), "Expected failure when replies are dropped");
    }

    @Test
    void faultDelayExceedingTimeoutCausesFailure() throws Exception {
        Path p = createTemplate("delay.properties", "faultDelayMs", "200", 50);
        Template t = Template.load(p.toString());
        assertFalse(Main.run(t, true, tempDir.toString()), "Expected failure when delay > timeoutMs");
    }

    @Test
    void faultCorruptFailsChecksumOrVerifier() throws Exception {
        Path p = tempDir.resolve("corrupt.properties");
        Properties props = new Properties();
        props.setProperty("port", "COM5");
        props.setProperty("baud", "9600");
        props.setProperty("dataBits", "8");
        props.setProperty("parity", "none");
        props.setProperty("stopBits", "1");
        props.setProperty("arraySize", "4");
        props.setProperty("send", "0A 1B 2C 3D");
        props.setProperty("expect", "A0 B1 C2 D3");
        props.setProperty("verify", "NIBBLE_SWAP");
        props.setProperty("device", "NIBBLE_SWAP");
        props.setProperty("timeoutMs", "1000");
        props.setProperty("checksum", "XOR");
        props.setProperty("faultCorrupt", "FLIP_BIT:1");
        props.store(Files.newOutputStream(p), null);

        Template t = Template.load(p.toString());
        assertFalse(Main.run(t, true, tempDir.toString()), "Expected checksum failure due to bit flip");
    }

    @Test
    void faultPartialBytesCausesIncompleteReadFailure() throws Exception {
        Path p = createTemplate("partial.properties", "faultPartialBytes", "2", 100);
        Template t = Template.load(p.toString());
        assertFalse(Main.run(t, true, tempDir.toString()), "Expected failure due to partial byte delivery");
    }
}
