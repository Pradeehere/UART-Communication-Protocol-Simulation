package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestLogTest {
    @TempDir
    Path tempDir;

    @Test
    void logsAlignedColumnsWithoutTrailingEmptyPipes() throws Exception {
        Path logFile = tempDir.resolve("test.log");
        try (TestLog log = new TestLog(logFile.toString())) {
            log.start("sample-template");
            log.log("TESTER", "SEND", new byte[]{0x0A, 0x1B}, null);
            log.log("TESTER", "RESULT", new byte[]{0x0A, 0x1B}, "PASS");
        }

        List<String> lines = Files.readAllLines(logFile);
        assertTrue(lines.get(0).startsWith("RUN START sample-template"));

        String sendLine = lines.get(1);
        assertTrue(sendLine.contains(" | TESTER | SEND    | 0A 1B"));
        assertFalse(sendLine.endsWith("|"), "Should not have trailing empty pipe");
        assertFalse(sendLine.endsWith("| "), "Should not have trailing empty pipe");

        String resultLine = lines.get(2);
        assertTrue(resultLine.contains(" | TESTER | RESULT  | 0A 1B | PASS"));
    }

    @Test
    void truncatesHexOver32Bytes() throws Exception {
        Path logFile = tempDir.resolve("trunc.log");
        byte[] exactly32 = new byte[32];
        byte[] over32 = new byte[35];
        for (int i = 0; i < 35; i++) {
            over32[i] = (byte) i;
            if (i < 32) exactly32[i] = (byte) i;
        }

        try (TestLog log = new TestLog(logFile.toString())) {
            log.log("TESTER", "SEND", exactly32, "full frame");
            log.log("TESTER", "SEND", over32, "truncated frame");
        }

        List<String> lines = Files.readAllLines(logFile);
        assertFalse(lines.get(0).contains("..."), "32 bytes must not be truncated");
        assertTrue(lines.get(1).contains(" ... (35 bytes) | truncated frame"), "35 bytes must have truncation suffix");
    }

    @Test
    void generatesDailyLogFilenames() {
        String today = java.time.LocalDate.now().toString();
        String runLog = TestLog.runLogPath("base");
        String devLog = TestLog.deviceLogPath("base");
        assertTrue(runLog.endsWith("run-" + today + ".log"));
        assertTrue(devLog.endsWith("device-" + today + ".log"));
    }
}

