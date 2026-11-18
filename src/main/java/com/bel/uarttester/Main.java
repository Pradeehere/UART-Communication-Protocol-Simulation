package com.bel.uarttester;

import com.fazecast.jSerialComm.SerialPort;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI entry point for UART tests, batch runs, port listing, result viewing, and
 * CSV export.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 3)
            usage();

        if ("ports".equalsIgnoreCase(args[0])) {
            SerialPort[] ports = SerialPort.getCommPorts();
            if (ports.length == 0)
                System.out.println("No serial ports detected.");
            else
                for (SerialPort p : ports)
                    System.out.println(p.getSystemPortName() + " - " + p.getDescriptivePortName());
            System.exit(0);
        }
        if ("export".equalsIgnoreCase(args[0])) {
            if (args.length < 2)
                usage();
            Path csv = Paths.get(args[1]);
            Path db = Paths.get(args.length == 3 ? args[2] : "data/results.db");
            try {
                int n = ResultStore.exportCsv(db, csv);
                System.out.println("Exported " + n + " rows to " + csv);
                System.exit(0);
            } catch (Exception e) {
                System.err.println("Export error: " + e.getMessage());
                System.exit(2);
            }
        }
        if ("results".equalsIgnoreCase(args[0])) {
            String db = args.length >= 2 ? args[1] : "data/results.db";
            try {
                ResultStore.printLastResults(db, 20);
                System.exit(0);
            } catch (Exception e) {
                System.err.println("Error reading results: " + e.getMessage());
                System.exit(2);
            }
        }

        boolean loopback = args.length >= 2 && "loopback".equalsIgnoreCase(args[1]);
        if (args.length == 2 && !loopback) {
            System.err.println("Unknown argument: " + args[1] + " (expected 'loopback')");
            System.exit(2);
        }

        Path target = Paths.get(args[0]);
        if (Files.isDirectory(target)) {
            try {
                System.exit(BatchRunner.run(target, loopback, "."));
            } catch (Exception e) {
                System.err.println("Batch error: " + e.getMessage());
                System.exit(2);
            }
        }

        try {
            Template t = Template.load(args[0]);
            System.exit(run(t, loopback, ".") ? 0 : 1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            try (TestLog errLog = new TestLog("logs/run.log")) {
                errLog.log("TESTER", "ERROR", null, e.getMessage());
            } catch (Exception ignored) {
            }
            System.exit(2);
        }
    }

    private static void usage() {
        System.err.println("Usage: Main <template|dir> [loopback] | ports | results [db] | export <file.csv> [db]");
        System.exit(2);
    }

    public static boolean run(Template template, boolean loopback) throws Exception {
        return run(template, loopback, ".");
    }

    public static boolean run(Template t, boolean loopback, String baseDir) throws Exception {
        String logPath = Paths.get(baseDir, "logs", "run.log").toString();
        String devLogPath = Paths.get(baseDir, "logs", "device.log").toString();
        String dbPath = Paths.get(baseDir, "data", "results.db").toString();

        try (TestLog log = new TestLog(logPath); ResultStore store = new ResultStore(dbPath)) {
            log.start(t.name);
            log.log("TESTER", "INFO", null, "Template loaded: " + t.name + " from " + t.source);
            DeviceSimulator sim = null;
            Thread simThread = null;
            TestLog deviceLog = null;
            UartPort testPort;
            UartPort simPort = null;

            if (loopback) {
                deviceLog = new TestLog(devLogPath);
                LoopbackUartPort[] pair = LoopbackUartPort.pair();
                testPort = pair[0];
                simPort = pair[1];
                if (t.baudTiming) {
                    pair[0].setBaudRate(t.baud, t.dataBits, t.parity, t.stopBits);
                    pair[1].setBaudRate(t.baud, t.dataBits, t.parity, t.stopBits);
                }
                sim = new DeviceSimulator(simPort, t, deviceLog);
                simThread = new Thread(sim, "device-simulator");
                simThread.start();
                log.log("TESTER", "INFO", null, "Loopback simulator started (" + t.device + ")");
            } else {
                testPort = new SerialUartPort(t);
                log.log("TESTER", "INFO", null, "Serial port opened: " + t.port);
            }

            try {
                TestRunner runner = new TestRunner(testPort, TestRunner.verifierFor(t.verify), log, store);
                return runner.run(t);
            } finally {
                if (sim != null) {
                    sim.stop();
                    simPort.close();
                    simThread.join(1000);
                    log.log("TESTER", "INFO", null, "Simulator stopped");
                }
                testPort.close();
                log.log("TESTER", "INFO", null, "Port closed");
                if (deviceLog != null)
                    deviceLog.close();
            }
        }
    }
}
