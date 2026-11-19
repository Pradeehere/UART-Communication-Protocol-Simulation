package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
    @TempDir
    Path tempDir;

    private Path writeTemplate(String name, String send, String expect, String verify, String device) throws Exception {
        Path p = tempDir.resolve(name);
        Properties props = new Properties();
        props.setProperty("port", "COM5");
        props.setProperty("baud", "9600");
        props.setProperty("dataBits", "8");
        props.setProperty("parity", "none");
        props.setProperty("stopBits", "1");
        props.setProperty("arraySize", "4");
        props.setProperty("send", send);
        props.setProperty("expect", expect);
        props.setProperty("verify", verify);
        props.setProperty("device", device);
        props.setProperty("timeoutMs", "1000");
        props.store(Files.newOutputStream(p), null);
        return p;
    }

    @Test
    void runsLoopbackPassReturnsTrue() throws Exception {
        Path tpl = writeTemplate("pass.properties", "0A 1B 2C 3D", "A0 B1 C2 D3", "NIBBLE_SWAP", "NIBBLE_SWAP");
        Template t = Template.load(tpl.toString());
        assertTrue(Main.run(t, true, tempDir.toString()));
    }

    @Test
    void runsLoopbackFailReturnsFalse() throws Exception {
        Path tpl = writeTemplate("fail.properties", "0A 1B 2C 3D", "A0 B1 C2 FF", "NIBBLE_SWAP", "NIBBLE_SWAP");
        Template t = Template.load(tpl.toString());
        assertFalse(Main.run(t, true, tempDir.toString()));
    }

    @Test
    void resultsCliOutputWorks() throws Exception {
        Path tpl = writeTemplate("rec.properties", "0A 1B 2C 3D", "A0 B1 C2 D3", "NIBBLE_SWAP", "NIBBLE_SWAP");
        Template t = Template.load(tpl.toString());
        Main.run(t, true, tempDir.toString());

        Path dbPath = tempDir.resolve("data").resolve("results.db");
        assertTrue(Files.exists(dbPath));
        ResultStore.printLastResults(dbPath.toString(), 20);
    }

    @Test
    void warnsWhenTimeoutShorterThanWireTime() throws Exception {
        Path p = tempDir.resolve("warn.properties");
        Properties props = new Properties();
        props.setProperty("port", "COM5");
        props.setProperty("baud", "1200");
        props.setProperty("dataBits", "8");
        props.setProperty("parity", "none");
        props.setProperty("stopBits", "1");
        props.setProperty("arraySize", "4");
        props.setProperty("send", "0A 1B 2C 3D");
        props.setProperty("expect", "A0 B1 C2 D3");
        props.setProperty("verify", "NIBBLE_SWAP");
        props.setProperty("device", "NIBBLE_SWAP");
        props.setProperty("timeoutMs", "10");
        props.setProperty("baudTiming", "true");
        props.store(Files.newOutputStream(p), null);

        Template t = Template.load(p.toString());
        Main.run(t, true, tempDir.toString());

        Path logPath = Paths.get(TestLog.runLogPath(tempDir.toString()));
        assertTrue(Files.exists(logPath));
        String logContent = Files.readString(logPath);
        assertTrue(logContent.contains("WARN"));
        assertTrue(logContent.contains("shorter than round-trip wire time"));
    }

    @Test
    void systemPropertyUarttesterOutDirectsLogsAndData() throws Exception {
        String prev = System.getProperty("uarttester.out");
        try {
            System.setProperty("uarttester.out", tempDir.toString());
            Path tpl = writeTemplate("sysprop.properties", "0A 1B 2C 3D", "A0 B1 C2 D3", "NIBBLE_SWAP", "NIBBLE_SWAP");
            Template t = Template.load(tpl.toString());
            assertTrue(Main.run(t, true));
            assertTrue(Files.exists(Paths.get(TestLog.runLogPath(tempDir.toString()))));
            assertTrue(Files.exists(tempDir.resolve("data").resolve("results.db")));
        } finally {
            if (prev != null) System.setProperty("uarttester.out", prev);
            else System.clearProperty("uarttester.out");
        }
    }
}
