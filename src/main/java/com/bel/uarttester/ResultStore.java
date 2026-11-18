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

/**
 * Saves completed UART array exchanges and verdicts to a SQLite database.
 */
public class ResultStore implements AutoCloseable {
    private final Connection db;

    public ResultStore(String path) throws SQLException, IOException {
        Path parent = Paths.get(path).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        db = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement s = db.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS results ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, created_at TEXT NOT NULL, "
                    + "template TEXT NOT NULL, array_no INTEGER NOT NULL, "
                    + "sent TEXT NOT NULL, received TEXT NOT NULL, expected TEXT NOT NULL, verdict TEXT NOT NULL)");
            try (ResultSet rs = s.executeQuery("PRAGMA table_info(results)")) {
                boolean hasTemplate = false, hasArrayNo = false;
                while (rs.next()) {
                    String col = rs.getString("name");
                    if ("template".equalsIgnoreCase(col)) hasTemplate = true;
                    if ("array_no".equalsIgnoreCase(col)) hasArrayNo = true;
                }
                if (!hasTemplate) s.execute("ALTER TABLE results ADD COLUMN template TEXT DEFAULT ''");
                if (!hasArrayNo) s.execute("ALTER TABLE results ADD COLUMN array_no INTEGER DEFAULT 1");
            }
        }
    }

    public void save(String template, int arrayNo, byte[] sent, byte[] received, byte[] expected, boolean pass) throws SQLException {
        String sql = "INSERT INTO results (created_at, template, array_no, sent, received, expected, verdict) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, LocalDateTime.now().toString());
            ps.setString(2, template != null ? template : "");
            ps.setInt(3, arrayNo);
            ps.setString(4, Template.toHex(sent));
            ps.setString(5, Template.toHex(received));
            ps.setString(6, Template.toHex(expected));
            ps.setString(7, pass ? "PASS" : "FAIL");
            ps.executeUpdate();
        }
    }

    public void save(byte[] sent, byte[] received, byte[] expected, boolean pass) {
        try {
            save("", 1, sent, received, expected, pass);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save result", e);
        }
    }

    public static void printLastResults(String path, int limit) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, created_at, template, array_no, sent, received, expected, verdict "
                     + "FROM (SELECT * FROM results ORDER BY id DESC LIMIT " + limit + ") ORDER BY id ASC")) {
            System.out.printf("%-4s | %-23s | %-16s | %-8s | %-12s | %-12s | %-12s | %-7s%n",
                    "ID", "TIMESTAMP", "TEMPLATE", "ARRAY_NO", "SENT", "RECEIVED", "EXPECTED", "VERDICT");
            System.out.println("-----+-------------------------+------------------+----------+--------------+--------------+--------------+--------");
            while (rs.next()) {
                System.out.printf("%-4d | %-23s | %-16s | %-8d | %-12s | %-12s | %-12s | %-7s%n",
                        rs.getInt("id"),
                        rs.getString("created_at"),
                        rs.getString("template"),
                        rs.getInt("array_no"),
                        rs.getString("sent"),
                        rs.getString("received"),
                        rs.getString("expected"),
                        rs.getString("verdict"));
            }
        }
    }

    @Override
    public void close() throws SQLException {
        db.close();
    }
}
