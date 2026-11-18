package com.bel.uarttester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Locale;

/** Saves completed UART array exchanges, latencies, and verdicts to a SQLite database. */
public class ResultStore implements AutoCloseable {
    private final Connection db;

    public ResultStore(String path) throws SQLException, IOException {
        Path parent = Paths.get(path).getParent();
        if (parent != null) Files.createDirectories(parent);
        db = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement s = db.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS results ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, created_at TEXT NOT NULL, "
                    + "template TEXT NOT NULL, array_no INTEGER NOT NULL, "
                    + "sent TEXT NOT NULL, received TEXT NOT NULL, expected TEXT NOT NULL, "
                    + "latency_ms REAL DEFAULT 0, verdict TEXT NOT NULL)");
            try (ResultSet rs = s.executeQuery("PRAGMA table_info(results)")) {
                boolean hasTpl = false, hasArr = false, hasLat = false;
                while (rs.next()) {
                    String col = rs.getString("name");
                    if ("template".equalsIgnoreCase(col)) hasTpl = true;
                    if ("array_no".equalsIgnoreCase(col)) hasArr = true;
                    if ("latency_ms".equalsIgnoreCase(col)) hasLat = true;
                }
                if (!hasTpl) s.execute("ALTER TABLE results ADD COLUMN template TEXT DEFAULT ''");
                if (!hasArr) s.execute("ALTER TABLE results ADD COLUMN array_no INTEGER DEFAULT 1");
                if (!hasLat) s.execute("ALTER TABLE results ADD COLUMN latency_ms REAL DEFAULT 0");
            }
        }
    }

    public void save(String template, int arrayNo, byte[] sent, byte[] received, String expected, double latencyMs, boolean pass) throws SQLException {
        String sql = "INSERT INTO results (created_at, template, array_no, sent, received, expected, latency_ms, verdict) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, LocalDateTime.now().toString());
            ps.setString(2, template != null ? template : "");
            ps.setInt(3, arrayNo);
            ps.setString(4, Template.toHex(sent));
            ps.setString(5, Template.toHex(received));
            ps.setString(6, expected != null ? expected : "");
            ps.setDouble(7, latencyMs);
            ps.setString(8, pass ? "PASS" : "FAIL");
            ps.executeUpdate();
        }
    }

    public void save(String tpl, int arr, byte[] sent, byte[] rec, byte[] exp, double lat, boolean pass) throws SQLException {
        save(tpl, arr, sent, rec, Template.toHex(exp), lat, pass);
    }

    public void save(String tpl, int arr, byte[] sent, byte[] rec, byte[] exp, boolean pass) throws SQLException {
        save(tpl, arr, sent, rec, exp, 0.0, pass);
    }

    public void save(byte[] sent, byte[] rec, byte[] exp, boolean pass) {
        try { save("", 1, sent, rec, exp, 0.0, pass); }
        catch (SQLException e) { throw new IllegalStateException("Could not save result", e); }
    }

    public static void printLastResults(String path, int limit) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, created_at, template, array_no, sent, received, expected, "
                     + "COALESCE(latency_ms, 0) AS latency_ms, verdict "
                     + "FROM (SELECT * FROM results ORDER BY id DESC LIMIT " + limit + ") ORDER BY id ASC")) {
            System.out.printf("%-4s | %-23s | %-16s | %-8s | %-12s | %-12s | %-12s | %-10s | %-7s%n",
                    "ID", "TIMESTAMP", "TEMPLATE", "ARRAY_NO", "SENT", "RECEIVED", "EXPECTED", "LATENCY_MS", "VERDICT");
            System.out.println("-----+-------------------------+------------------+----------+--------------+--------------+--------------+------------+--------");
            int count = 0; double min = Double.MAX_VALUE, max = 0.0, sum = 0.0;
            while (rs.next()) {
                double lat = rs.getDouble("latency_ms");
                count++; sum += lat;
                if (lat < min) min = lat;
                if (lat > max) max = lat;
                System.out.printf(Locale.US, "%-4d | %-23s | %-16s | %-8d | %-12s | %-12s | %-12s | %-10.2f | %-7s%n",
                        rs.getInt("id"), rs.getString("created_at"), rs.getString("template"),
                        rs.getInt("array_no"), rs.getString("sent"), rs.getString("received"),
                        rs.getString("expected"), lat, rs.getString("verdict"));
            }
            if (count > 0) {
                System.out.printf(Locale.US, "Latency summary: min = %.2f ms, avg = %.2f ms, max = %.2f ms%n", min, sum / count, max);
            } else {
                System.out.println("Latency summary: min = 0.00 ms, avg = 0.00 ms, max = 0.00 ms");
            }
        }
    }

    public static int exportCsv(Path dbPath, Path csvPath) throws Exception {
        return CsvExporter.export(dbPath, csvPath);
    }

    @Override
    public void close() throws SQLException { db.close(); }
}
