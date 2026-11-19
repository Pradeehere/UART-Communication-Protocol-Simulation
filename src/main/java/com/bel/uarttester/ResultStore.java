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
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Saves completed UART array exchanges, latencies, and verdicts to a SQLite database. */
public class ResultStore implements AutoCloseable {
    private static final DateTimeFormatter RUN_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final Connection db;
    private final String runId;
    private int uncommitted = 0;

    public ResultStore(String path) throws SQLException, IOException {
        this(path, generateRunId());
    }

    public ResultStore(String path, String runId) throws SQLException, IOException {
        this.runId = runId != null ? runId : generateRunId();
        Path parent = Paths.get(path).getParent();
        if (parent != null) Files.createDirectories(parent);
        db = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement s = db.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL;");
        }
        db.setAutoCommit(false);
        try (Statement s = db.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS results ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, run_id TEXT DEFAULT '', "
                    + "created_at TEXT NOT NULL, template TEXT NOT NULL, array_no INTEGER NOT NULL, "
                    + "sent TEXT NOT NULL, received TEXT NOT NULL, expected TEXT NOT NULL, "
                    + "latency_ms REAL DEFAULT 0, verdict TEXT NOT NULL, detail TEXT DEFAULT '')");
            ensureColumn(db, "template", "TEXT DEFAULT ''");
            ensureColumn(db, "array_no", "INTEGER DEFAULT 1");
            ensureColumn(db, "latency_ms", "REAL DEFAULT 0");
            ensureColumn(db, "run_id", "TEXT DEFAULT ''");
            ensureColumn(db, "detail", "TEXT DEFAULT ''");
            s.execute("CREATE INDEX IF NOT EXISTS idx_results_tpl_time ON results(template, created_at)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_results_run_id ON results(run_id)");
        }
    }

    public static String generateRunId() {
        return LocalDateTime.now().format(RUN_FMT) + "_" + Long.toHexString(System.nanoTime() & 0xFFFF);
    }

    public String getRunId() { return runId; }

    private void ensureColumn(Connection conn, String col, String ddl) throws SQLException {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("PRAGMA table_info(results)")) {
            while (rs.next()) {
                if (col.equalsIgnoreCase(rs.getString("name"))) return;
            }
        }
        try (Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE results ADD COLUMN " + col + " " + ddl);
        }
    }

    static String capHex(String s, int maxBytes) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains("...")) return s;
        String[] parts = s.trim().split("\\s+");
        if (parts.length <= maxBytes) return s;
        StringBuilder sb = new StringBuilder(maxBytes * 3 + 32);
        for (int i = 0; i < maxBytes; i++) {
            if (i > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        sb.append(" ... (").append(parts.length).append(" bytes)");
        return sb.toString();
    }

    public void save(String tpl, int arr, byte[] sent, byte[] rec, String exp, double lat, boolean pass, String detail) throws SQLException {
        String sql = "INSERT INTO results (run_id, created_at, template, array_no, sent, received, expected, latency_ms, verdict, detail) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setString(2, LocalDateTime.now().toString());
            ps.setString(3, tpl != null ? tpl : "");
            ps.setInt(4, arr);
            ps.setString(5, Template.toHex(sent, 4096));
            ps.setString(6, Template.toHex(rec, 4096));
            ps.setString(7, capHex(exp, 4096));
            ps.setDouble(8, lat);
            ps.setString(9, pass ? "PASS" : "FAIL");
            ps.setString(10, detail != null ? detail : "");
            ps.executeUpdate();
        }
        if (++uncommitted >= 1000) {
            db.commit();
            uncommitted = 0;
        }
    }

    public void save(String tpl, int arr, byte[] sent, byte[] rec, String exp, double lat, boolean pass) throws SQLException {
        save(tpl, arr, sent, rec, exp, lat, pass, "");
    }

    public void save(String tpl, int arr, byte[] sent, byte[] rec, byte[] exp, boolean pass) throws SQLException {
        save(tpl, arr, sent, rec, Template.toHex(exp, 4096), 0.0, pass, "");
    }

    public static void printLastResults(String path, int limit) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, COALESCE(run_id, '') AS run_id, created_at, template, array_no, "
                     + "sent, received, expected, COALESCE(latency_ms, 0) AS latency_ms, verdict "
                     + "FROM (SELECT * FROM results ORDER BY id DESC LIMIT " + limit + ") ORDER BY id ASC")) {
            System.out.printf("%-4s | %-20s | %-23s | %-16s | %-8s | %-12s | %-12s | %-12s | %-10s | %-7s%n",
                    "ID", "RUN_ID", "TIMESTAMP", "TEMPLATE", "ARRAY_NO", "SENT", "RECEIVED", "EXPECTED", "LATENCY_MS", "VERDICT");
            System.out.println("-----+----------------------+-------------------------+------------------+----------+--------------+--------------+--------------+------------+--------");
            int count = 0; double min = Double.MAX_VALUE, max = 0.0, sum = 0.0;
            while (rs.next()) {
                double lat = rs.getDouble("latency_ms");
                count++; sum += lat;
                if (lat < min) min = lat;
                if (lat > max) max = lat;
                System.out.printf(Locale.US, "%-4d | %-20s | %-23s | %-16s | %-8d | %-12s | %-12s | %-12s | %-10.2f | %-7s%n",
                        rs.getInt("id"), rs.getString("run_id"), rs.getString("created_at"), rs.getString("template"),
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
    public void close() throws SQLException {
        try {
            if (!db.isClosed() && !db.getAutoCommit()) {
                db.commit();
            }
        } finally {
            db.close();
        }
    }
}
