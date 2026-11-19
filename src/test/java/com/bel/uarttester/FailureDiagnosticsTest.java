package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FailureDiagnosticsTest {
    @TempDir
    Path tempDir;

    private Properties baseProps(String name, String send, String expect) {
        Properties p = new Properties();
        p.setProperty("name", name);
        p.setProperty("port", "COM5");
        p.setProperty("baud", "9600");
        p.setProperty("dataBits", "8");
        p.setProperty("parity", "none");
        p.setProperty("stopBits", "1");
        p.setProperty("arraySize", "4");
        p.setProperty("send", send);
        p.setProperty("expect", expect);
        p.setProperty("verify", "EXACT_MATCH");
        p.setProperty("device", "ECHO");
        p.setProperty("timeoutMs", "100");
        return p;
    }

    private String getDetailFromDb(Path dbPath) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT detail FROM results ORDER BY id DESC LIMIT 1")) {
            assertTrue(rs.next());
            return rs.getString("detail");
        }
    }

    @Test
    void timeoutFailureReportsTimeoutDiagnostic() throws Exception {
        Properties p = baseProps("diag-timeout", "01 02 03 04", "01 02 03 04");
        p.setProperty("device", "SILENT");
        Path propFile = tempDir.resolve("timeout.properties");
        p.store(Files.newOutputStream(propFile), null);

        Template t = Template.load(propFile.toString());
        assertFalse(Main.run(t, true, tempDir.toString()));

        Path dbPath = tempDir.resolve("data").resolve("results.db");
        String detail = getDetailFromDb(dbPath);
        assertTrue(detail.startsWith("FAIL - timeout: no reply within 100 ms"), "Actual: " + detail);
    }

    @Test
    void shortReplyFailureReportsByteCounts() throws Exception {
        Properties p = baseProps("diag-short", "01 02 03 04", "01 02 03 04");
        p.setProperty("faultPartialBytes", "2");
        Path propFile = tempDir.resolve("short.properties");
        p.store(Files.newOutputStream(propFile), null);

        Template t = Template.load(propFile.toString());
        assertFalse(Main.run(t, true, tempDir.toString()));

        Path dbPath = tempDir.resolve("data").resolve("results.db");
        String detail = getDetailFromDb(dbPath);
        assertTrue(detail.startsWith("FAIL - short reply: 2 of 4 bytes"), "Actual: " + detail);
    }

    @Test
    void frameErrorReportsBadFrame() throws Exception {
        Properties p = baseProps("diag-frame", "01 02 03 04", "01 02 03 04");
        p.setProperty("checksum", "XOR");
        p.setProperty("faultCorrupt", "FLIP_BIT:0");
        Path propFile = tempDir.resolve("frame.properties");
        p.store(Files.newOutputStream(propFile), null);

        Template t = Template.load(propFile.toString());
        assertFalse(Main.run(t, true, tempDir.toString()));

        Path dbPath = tempDir.resolve("data").resolve("results.db");
        String detail = getDetailFromDb(dbPath);
        assertTrue(detail.startsWith("FAIL - bad frame: Checksum mismatch"), "Actual: " + detail);
    }

    @Test
    void contentMismatchReportsDifferingPositionsAndSkipsWildcards() throws Exception {
        Properties p = baseProps("diag-diff", "0A 1B 2C 3D", "0A ?? 2C FF");
        // Device is ECHO, so received is 0A 1B 2C 3D
        // Expected is 0A ?? 2C FF: byte 0 matches (0A), byte 1 wildcard (??), byte 2 matches (2C), byte 3 differs (FF vs 3D)
        Path propFile = tempDir.resolve("diff.properties");
        p.store(Files.newOutputStream(propFile), null);

        Template t = Template.load(propFile.toString());
        assertFalse(Main.run(t, true, tempDir.toString()));

        Path dbPath = tempDir.resolve("data").resolve("results.db");
        String detail = getDetailFromDb(dbPath);
        assertTrue(detail.contains("1 of 4 bytes differ; first at byte 3: expected FF, got 3D"), "Actual: " + detail);
    }
}
