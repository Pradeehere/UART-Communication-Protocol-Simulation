package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchRunnerTest {
    @TempDir
    Path tempDir;

    private void writeTemplate(Path dir, String name, String send, String expect) throws Exception {
        Properties p = new Properties();
        p.setProperty("port", "COM5");
        p.setProperty("baud", "9600");
        p.setProperty("dataBits", "8");
        p.setProperty("parity", "none");
        p.setProperty("stopBits", "1");
        p.setProperty("arraySize", "2");
        p.setProperty("send", send);
        p.setProperty("expect", expect);
        p.setProperty("verify", "EXACT_MATCH");
        p.setProperty("device", "ECHO");
        p.setProperty("timeoutMs", "1000");
        p.store(Files.newOutputStream(dir.resolve(name)), null);
    }

    @Test
    void allPassReturnsExitCodeZero() throws Exception {
        Path tplDir = tempDir.resolve("templates-pass");
        Files.createDirectories(tplDir);
        writeTemplate(tplDir, "t1.properties", "01 02", "01 02");
        writeTemplate(tplDir, "t2.properties", "03 04", "03 04");

        int code = BatchRunner.run(tplDir, true, tempDir.toString());
        assertEquals(0, code);
    }

    @Test
    void mixedPassAndFailReturnsExitCodeOne() throws Exception {
        Path tplDir = tempDir.resolve("templates-mixed");
        Files.createDirectories(tplDir);
        writeTemplate(tplDir, "t1.properties", "01 02", "01 02");
        writeTemplate(tplDir, "t2-fail.properties", "03 04", "03 FF");

        int code = BatchRunner.run(tplDir, true, tempDir.toString());
        assertEquals(1, code);
    }

    @Test
    void emptyDirectoryReturnsExitCodeTwo() throws Exception {
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);

        int code = BatchRunner.run(emptyDir, true, tempDir.toString());
        assertEquals(2, code);
    }
}
