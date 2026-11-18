package com.bel.uarttester;

import java.nio.file.Paths;

/**
 * CLI entry point for running UART protocol tests or viewing stored results.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: Main <template-file> [loopback] | Main results [db-path]");
            System.exit(2);
        }
        if ("results".equalsIgnoreCase(args[0])) {
            String db = args.length == 2 ? args[1] : "data/results.db";
            try {
                ResultStore.printLastResults(db, 20);
                System.exit(0);
            } catch (Exception e) {
                System.err.println("Error reading results: " + e.getMessage());
                System.exit(2);
            }
        }
        boolean loopback = false;
        if (args.length == 2) {
            if (!"loopback".equalsIgnoreCase(args[1])) {
                System.err.println("Unknown argument: " + args[1] + " (expected 'loopback')");
                System.exit(2);
            }
            loopback = true;
        }
        try {
            Template template = Template.load(args[0]);
            boolean pass = run(template, loopback, ".");
            System.exit(pass ? 0 : 1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            try (TestLog errLog = new TestLog("logs/run.log")) {
                errLog.log("TESTER", "ERROR", null, e.getMessage());
            } catch (Exception ignored) {}
            System.exit(2);
        }
    }

    public static boolean run(Template template, boolean loopback) throws Exception {
        return run(template, loopback, ".");
    }

    public static boolean run(Template template, boolean loopback, String baseDir) throws Exception {
        String logPath = Paths.get(baseDir, "logs", "run.log").toString();
        String devLogPath = Paths.get(baseDir, "logs", "device.log").toString();
        String dbPath = Paths.get(baseDir, "data", "results.db").toString();

        try (TestLog log = new TestLog(logPath);
             ResultStore store = new ResultStore(dbPath)) {
            log.start(template.name);
            log.log("TESTER", "INFO", null, "Template loaded: " + template.name + " from " + template.source);

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
                sim = new DeviceSimulator(simPort, template.arraySize, deviceLog, template.device);
                simThread = new Thread(sim, "device-simulator");
                simThread.start();
                log.log("TESTER", "INFO", null, "Loopback simulator started (" + template.device + ")");
            } else {
                testPort = new SerialUartPort(template);
                log.log("TESTER", "INFO", null, "Serial port opened: " + template.port);
            }

            try {
                TestRunner runner = new TestRunner(testPort, TestRunner.verifierFor(template.verify), log, store);
                return runner.run(template);
            } finally {
                if (sim != null) {
                    sim.stop();
                    simPort.close();
                    simThread.join(1000);
                    log.log("TESTER", "INFO", null, "Simulator stopped");
                }
                testPort.close();
                log.log("TESTER", "INFO", null, "Port closed");
                if (deviceLog != null) deviceLog.close();
            }
        }
    }
}
