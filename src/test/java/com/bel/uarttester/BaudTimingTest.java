package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaudTimingTest {
    @TempDir
    Path tempDir;

    @Test
    void lowBaudRateCausesTimeoutWhenBaudTimingEnabled() throws Exception {
        // At 300 baud with 10 bits/byte: 33.3 ms per byte.
        // For 4 bytes sent + 4 bytes returned = ~266 ms total wire time.
        // With timeoutMs = 40 ms, reading 4 bytes will time out.
        Path p = tempDir.resolve("baud-slow.properties");
        Properties props = new Properties();
        props.setProperty("port", "COM5");
        props.setProperty("baud", "300");
        props.setProperty("dataBits", "8");
        props.setProperty("parity", "none");
        props.setProperty("stopBits", "1");
        props.setProperty("arraySize", "4");
        props.setProperty("send", "0A 1B 2C 3D");
        props.setProperty("expect", "A0 B1 C2 D3");
        props.setProperty("verify", "NIBBLE_SWAP");
        props.setProperty("device", "NIBBLE_SWAP");
        props.setProperty("timeoutMs", "40");
        props.setProperty("baudTiming", "true");
        props.store(Files.newOutputStream(p), null);

        Template t = Template.load(p.toString());
        assertFalse(Main.run(t, true, tempDir.toString()), "Low baud rate should timeout with short timeoutMs");
    }

    @Test
    void sameConfigurationPassesWhenBaudTimingDisabled() throws Exception {
        // Same 300 baud and 40ms timeout, but baudTiming=false -> fast loopback -> passes
        Path p = tempDir.resolve("baud-fast.properties");
        Properties props = new Properties();
        props.setProperty("port", "COM5");
        props.setProperty("baud", "300");
        props.setProperty("dataBits", "8");
        props.setProperty("parity", "none");
        props.setProperty("stopBits", "1");
        props.setProperty("arraySize", "4");
        props.setProperty("send", "0A 1B 2C 3D");
        props.setProperty("expect", "A0 B1 C2 D3");
        props.setProperty("verify", "NIBBLE_SWAP");
        props.setProperty("device", "NIBBLE_SWAP");
        props.setProperty("timeoutMs", "1000");
        props.setProperty("baudTiming", "false");
        props.store(Files.newOutputStream(p), null);

        Template t = Template.load(p.toString());
        assertTrue(Main.run(t, true, tempDir.toString()));
    }
}
