package com.bel.uarttester;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Standardized single-line event logger outputting to console and log file.
 * Format: timestamp | SOURCE | EVENT | hex bytes | detail
 */
public class TestLog implements AutoCloseable {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final PrintWriter file;

    public TestLog(String path) throws IOException {
        if (Paths.get(path).getParent() != null) {
            Files.createDirectories(Paths.get(path).getParent());
        }
        file = new PrintWriter(new FileWriter(path, true), true);
    }

    public void start(String templateName) {
        String line = "RUN START " + templateName;
        System.out.println(line);
        file.println(line);
    }

    public void log(String source, String event, byte[] bytes, String detail) {
        String hex = Template.toHex(bytes);
        String line = String.format("%s | %-6s | %-7s | %s",
                LocalDateTime.now().format(TIME), source, event, hex);
        if (detail != null && !detail.trim().isEmpty()) {
            line = line + " | " + detail.trim();
        }
        System.out.println(line);
        file.println(line);
    }

    public void log(String source, String event, String detail) {
        log(source, event, null, detail);
    }

    public void log(String event, byte[] bytes, String detail) {
        log("TESTER", event, bytes, detail);
    }

    @Override
    public void close() {
        file.close();
    }
}
