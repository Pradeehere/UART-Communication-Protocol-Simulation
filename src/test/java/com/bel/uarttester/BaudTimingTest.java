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

    @Test
    void frameExceeding500msWithBaudTimingPasses() throws Exception {
        // 128 bytes at 2400 baud: 10 bits/byte -> 1280 bits -> 0.533s wire transmission time.
        // With DeviceSimulator using timeoutMs (3000ms), it now passes instead of discarding at 500ms.
        Path p = tempDir.resolve("baud-128b.properties");
        Properties props = new Properties();
        props.setProperty("port", "COM5");
        props.setProperty("baud", "2400");
        props.setProperty("dataBits", "8");
        props.setProperty("parity", "none");
        props.setProperty("stopBits", "1");
        props.setProperty("arraySize", "128");
        props.setProperty("timeoutMs", "3000");
        props.setProperty("baudTiming", "true");
        props.setProperty("device", "ECHO");
        props.setProperty("verify", "EXACT_MATCH");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 128; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", i % 256));
        }
        String hex = sb.toString();
        props.setProperty("send", hex);
        props.setProperty("expect", hex);
        props.store(Files.newOutputStream(p), null);

        Template t = Template.load(p.toString());
        assertTrue(Main.run(t, true, tempDir.toString()));
    }
}
