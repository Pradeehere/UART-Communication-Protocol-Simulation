package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResultStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void createsSchemaAndPersistsResults() throws Exception {
        Path db = tempDir.resolve("test.db");
        try (ResultStore store = new ResultStore(db.toString())) {
            store.save("tpl", 1, new byte[]{0x0A}, new byte[]{(byte) 0xA0}, new byte[]{(byte) 0xA0}, true);
            store.save("tpl", 2, new byte[]{0x0B}, new byte[]{(byte) 0xB0}, new byte[]{(byte) 0xB0}, true);
        }

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT template, array_no, verdict FROM results ORDER BY id ASC")) {
            assertTrue(rs.next());
            assertEquals("tpl", rs.getString("template"));
            assertEquals(1, rs.getInt("array_no"));
            assertEquals("PASS", rs.getString("verdict"));

            assertTrue(rs.next());
            assertEquals(2, rs.getInt("array_no"));
        }

        // Test printLastResults without throwing
        ResultStore.printLastResults(db.toString(), 20);
    }

    @Test
    void migratesLegacySchemaGracefully() throws Exception {
        Path db = tempDir.resolve("legacy.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE results (id INTEGER PRIMARY KEY, created_at TEXT, sent TEXT, received TEXT, expected TEXT, verdict TEXT)");
        }

        try (ResultStore store = new ResultStore(db.toString())) {
            store.save("migrated", 1, new byte[]{0x01}, new byte[]{0x01}, new byte[]{0x01}, true);
        }

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(results)")) {
            Set<String> cols = new HashSet<>();
            while (rs.next()) cols.add(rs.getString("name"));
            assertTrue(cols.contains("template"));
            assertTrue(cols.contains("array_no"));
        }
    }
}

