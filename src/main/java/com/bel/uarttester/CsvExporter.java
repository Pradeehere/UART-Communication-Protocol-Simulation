package com.bel.uarttester;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Exports all rows from the SQLite results table to a UTF-8 CSV file.
 * Fields containing commas or quotes are properly escaped per RFC 4180.
 */
public class CsvExporter {
    private static final String HEADER = "id,timestamp,template,array_no,sent,received,expected,latency_ms,verdict";

    public static int export(Path dbPath, Path csvPath) throws Exception {
        int count = 0;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, created_at, template, array_no, "
                     + "sent, received, expected, COALESCE(latency_ms, 0) AS latency_ms, verdict "
                     + "FROM results ORDER BY id ASC");
             BufferedWriter w = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            w.write(HEADER);
            w.newLine();
            while (rs.next()) {
                w.write(String.join(",",
                        String.valueOf(rs.getInt("id")),
                        esc(rs.getString("created_at")),
                        esc(rs.getString("template")),
                        String.valueOf(rs.getInt("array_no")),
                        esc(rs.getString("sent")),
                        esc(rs.getString("received")),
                        esc(rs.getString("expected")),
                        String.format("%.2f", rs.getDouble("latency_ms")),
                        rs.getString("verdict")));
                w.newLine();
                count++;
            }
        }
        return count;
    }

    /** Escapes a CSV field: wraps in quotes if it contains comma, quote, or newline. */
    static String esc(String v) {
        if (v == null) return "";
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0)
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }
}
