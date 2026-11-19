package com.bel.uarttester;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Runs all .properties templates in a directory sequentially and prints a summary table. */
public class BatchRunner {
    public static class Entry {
        public final String name;
        public final int arrays;
        public final String status;
        public Entry(String name, int arrays, String status) {
            this.name = name; this.arrays = arrays; this.status = status;
        }
    }

    public static int run(Path dir, boolean loopback, String baseDir) throws Exception {
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.toString().endsWith(".properties"))
                          .sorted()
                          .collect(Collectors.toList());
        }
        if (files.isEmpty()) {
            System.err.println("No .properties templates found in directory: " + dir);
            return 2;
        }

        List<Entry> results = new ArrayList<>();
        int passed = 0, failed = 0, errors = 0;

        for (Path file : files) {
            Template t;
            try {
                t = Template.load(file.toString());
            } catch (Exception e) {
                System.err.println("Error loading " + file.getFileName() + ": " + e.getMessage());
                try (TestLog errLog = new TestLog(TestLog.runLogPath(baseDir))) {
                    errLog.log("TESTER", "ERROR", null, "Error loading " + file.getFileName() + ": " + e.getMessage());
                } catch (Exception ignored) {
                }
                results.add(new Entry(file.getFileName().toString(), 0, "ERROR"));
                errors++;
                continue;
            }
            try {
                boolean ok = Main.run(t, loopback, baseDir);
                int arrays = t.send.length / t.arraySize;
                if (ok) {
                    results.add(new Entry(t.name, arrays, "PASS"));
                    passed++;
                } else {
                    results.add(new Entry(t.name, arrays, "FAIL"));
                    failed++;
                }
            } catch (Exception e) {
                System.err.println("Error executing " + t.name + ": " + e.getMessage());
                results.add(new Entry(t.name, t.send.length / t.arraySize, "ERROR"));
                errors++;
            }
        }

        printSummary(results, passed, failed, errors);
        if (errors > 0) return 2;
        if (failed > 0) return 1;
        return 0;
    }

    private static void printSummary(List<Entry> list, int passed, int failed, int errors) {
        System.out.println();
        System.out.printf("%-20s | %-6s | %-6s%n", "TEMPLATE", "ARRAYS", "STATUS");
        System.out.println("---------------------+--------+-------");
        for (Entry e : list) {
            System.out.printf("%-20s | %-6d | %-6s%n", e.name, e.arrays, e.status);
        }
        System.out.println("---------------------+--------+-------");
        System.out.printf("Batch Summary: %d passed, %d failed, %d errors%n", passed, failed, errors);
    }
}
