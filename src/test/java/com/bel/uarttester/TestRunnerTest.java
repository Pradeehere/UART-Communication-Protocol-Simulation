package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestRunnerTest {
    @TempDir
    Path tempDir;

    public static class CustomVerifier implements Verifier {
        @Override
        public boolean verify(byte[] sent, byte[] received, byte[] expected) {
            return true;
        }
    }

    private Properties makeProps(String send, String expect, int arraySize, int timeoutMs) {
        Properties p = new Properties();
        p.setProperty("name", "multi-test");
        p.setProperty("port", "COM5");
        p.setProperty("baud", "9600");
        p.setProperty("dataBits", "8");
        p.setProperty("parity", "none");
        p.setProperty("stopBits", "1");
        p.setProperty("arraySize", String.valueOf(arraySize));
        p.setProperty("send", send);
        p.setProperty("expect", expect);
        p.setProperty("verify", "EXACT_MATCH");
        p.setProperty("timeoutMs", String.valueOf(timeoutMs));
        return p;
    }

    @Test
    void multiArrayBufferFillStoreCycle() throws Exception {
        // 12 bytes with arraySize=4 -> 3 arrays
        Properties p = makeProps("01 02 03 04 05 06 07 08 09 0A 0B 0C",
                                 "01 02 03 04 05 06 07 08 09 0A 0B 0C", 4, 1000);
        Template t = new Template(p);

        LoopbackUartPort[] pair = LoopbackUartPort.pair();
        Path logPath = tempDir.resolve("run.log");
        Path devLog = tempDir.resolve("dev.log");
        Path dbPath = tempDir.resolve("results.db");

        try (TestLog log = new TestLog(logPath.toString());
             TestLog dLog = new TestLog(devLog.toString());
             ResultStore store = new ResultStore(dbPath.toString());
             UartPort testPort = pair[0];
             UartPort simPort = pair[1]) {

            DeviceSimulator sim = new DeviceSimulator(simPort, 4, dLog, "ECHO");
            Thread th = new Thread(sim);
            th.start();

            TestRunner runner = new TestRunner(testPort, new ExactMatchVerifier(), log, store);
            assertTrue(runner.run(t));

            sim.stop();
            simPort.close();
            th.join(1000);
        }

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) as cnt FROM results")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt("cnt"), "Expected 3 persisted DB rows for 3 arrays");
        }
    }

    @Test
    void timeoutHandlesFailureGracefully() throws Exception {
        Properties p = makeProps("01 02", "01 02", 2, 50);
        Template t = new Template(p);

        LoopbackUartPort[] pair = LoopbackUartPort.pair();
        Path logPath = tempDir.resolve("timeout.log");
        try (TestLog log = new TestLog(logPath.toString());
             UartPort testPort = pair[0];
             UartPort silentPort = pair[1]) {
            TestRunner runner = new TestRunner(testPort, new ExactMatchVerifier(), log, null);
            assertFalse(runner.run(t));
        }
    }

    @Test
    void loadsCustomVerifierViaReflection() {
        Verifier v = TestRunner.verifierFor("com.bel.uarttester.TestRunnerTest$CustomVerifier");
        assertTrue(v instanceof CustomVerifier);
        assertTrue(v.verify(new byte[1], new byte[1], new byte[1]));
    }
}

