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
}

