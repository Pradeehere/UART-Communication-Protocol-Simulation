package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResultStoreAndExportTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsLatencyAndExportsCsvWithEscaping() throws Exception {
        Path db = tempDir.resolve("test.db");
        try (ResultStore store = new ResultStore(db.toString())) {
            store.save("my,template", 1, new byte[]{0x0A}, new byte[]{0x0A}, "0A", 1.45, true);
            store.save("my\"template", 2, new byte[]{0x0B}, new byte[]{0x0C}, "0B", 2.10, false);
        }

        // Verify stored latency in SQLite
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT latency_ms FROM results ORDER BY id ASC")) {
            assertTrue(rs.next());
            assertEquals(1.45, rs.getDouble("latency_ms"), 0.001);
            assertTrue(rs.next());
            assertEquals(2.10, rs.getDouble("latency_ms"), 0.001);
        }

        // Verify CSV export
        Path csv = tempDir.resolve("export.csv");
        int count = ResultStore.exportCsv(db, csv);
        assertEquals(2, count);

        List<String> lines = Files.readAllLines(csv);
        assertEquals(3, lines.size()); // header + 2 rows
        assertEquals("id,timestamp,template,array_no,sent,received,expected,latency_ms,verdict", lines.get(0));

        // Check comma escaping (wrapped in quotes)
        assertTrue(lines.get(1).contains("\"my,template\""));
        assertTrue(lines.get(1).contains("1.45"));
        assertTrue(lines.get(1).contains("PASS"));

        // Check quote escaping (doubled quotes)
        assertTrue(lines.get(2).contains("\"my\"\"template\""));
        assertTrue(lines.get(2).contains("2.10"));
        assertTrue(lines.get(2).contains("FAIL"));
    }

    @Test
    void csvEscapesQuotesAndCommasMethod() {
        assertEquals("\"hello,world\"", CsvExporter.esc("hello,world"));
        assertEquals("\"hello\"\"world\"", CsvExporter.esc("hello\"world"));
        assertEquals("simple", CsvExporter.esc("simple"));
        assertEquals("", CsvExporter.esc(null));
    }
}
