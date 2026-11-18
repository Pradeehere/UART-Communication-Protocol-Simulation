package com.bel.uarttester;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/** Fake UART device responding to incoming byte frames according to a Template rule. */
public class DeviceSimulator implements Runnable {
    private final UartPort port;
    private final int arraySize;
    private final TestLog log;
    private final String deviceRule;
    private volatile boolean running = true;

    public DeviceSimulator(UartPort port, int arraySize, TestLog log) {
        this(port, arraySize, log, "NIBBLE_SWAP");
    }

    public DeviceSimulator(UartPort port, int arraySize, TestLog log, String rule) {
        this.port = port;
        this.arraySize = arraySize;
        this.log = log;
        this.deviceRule = rule != null ? rule : "NIBBLE_SWAP";
    }

    @Override
    public void run() {
        while (running) {
            byte[] request = port.read(arraySize, 500);
            if (request.length == 0) continue;
            if (request.length != arraySize) {
                log.log("DEVICE", "WARN", request, "Incomplete frame discarded (" + request.length + "/" + arraySize + " bytes)");
                continue;
            }
            log.log("DEVICE", "RECEIVE", request, null);
            if ("SILENT".equalsIgnoreCase(deviceRule)) continue;
            byte[] reply = respond(request, deviceRule);
            port.write(reply);
            log.log("DEVICE", "SEND", reply, null);
        }
    }

    public void stop() { running = false; }

    static byte[] respond(byte[] request) { return respond(request, "NIBBLE_SWAP"); }

    static byte[] respond(byte[] request, String rule) {
        String u = rule.toUpperCase();
        if (u.equals("NIBBLE_SWAP")) {
            byte[] reply = new byte[request.length];
            for (int i = 0; i < request.length; i++) reply[i] = NibbleSwapVerifier.swap(request[i]);
            return reply;
        }
        if (u.equals("ECHO")) return request.clone();
        if (u.startsWith("ADD:")) {
            byte add = (byte) Integer.parseInt(rule.substring(4).trim(), 16);
            byte[] reply = new byte[request.length];
            for (int i = 0; i < request.length; i++) reply[i] = (byte) ((request[i] + add) & 0xFF);
            return reply;
        }
        if (u.startsWith("XOR:")) {
            byte xor = (byte) Integer.parseInt(rule.substring(4).trim(), 16);
            byte[] reply = new byte[request.length];
            for (int i = 0; i < request.length; i++) reply[i] = (byte) (request[i] ^ xor);
            return reply;
        }
        if (u.startsWith("FIXED:")) return Template.parseHex(rule.substring(6).trim());
        throw new IllegalArgumentException("Unknown device rule: " + rule);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: DeviceSimulator <template-file> [device-port]");
            System.exit(2);
        }
        Properties p = new Properties();
        try (java.io.Reader r = Files.newBufferedReader(Paths.get(args[0]), StandardCharsets.UTF_8)) { p.load(r); }
        if (args.length == 2) p.setProperty("port", args[1]);
        Template t = new Template(p, args[0]);
        try (TestLog log = new TestLog("logs/device.log")) {
            UartPort port = new SerialUartPort(t);
            System.out.println("Device simulator running on " + t.port + " (" + t.device + "). Press Ctrl+C to stop.");
            new DeviceSimulator(port, t.arraySize, log, t.device).run();
        }
    }
}
