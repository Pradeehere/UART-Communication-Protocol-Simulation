package com.bel.uarttester;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

/** Fake UART device responding to incoming byte frames according to template rules and faults. */
public class DeviceSimulator implements Runnable {
    private final UartPort port;
    private final int arraySize;
    private final TestLog log;
    private final String deviceRule;
    private final FrameCodec codec;
    private final int timeoutMs;
    private final int faultDelayMs, faultPartialBytes;
    private final boolean faultDrop;
    private final String faultCorrupt;
    private volatile boolean running = true;

    public DeviceSimulator(UartPort port, int arraySize, TestLog log) {
        this(port, arraySize, log, "NIBBLE_SWAP");
    }

    public DeviceSimulator(UartPort port, int arraySize, TestLog log, String rule) {
        this(port, arraySize, log, rule, new FrameCodec(), 1000, 0, false, "NONE", 0);
    }

    public DeviceSimulator(UartPort port, Template t, TestLog log) {
        this(port, t.arraySize, log, t.device, t.codec(), t.timeoutMs, t.faultDelayMs, t.faultDrop, t.faultCorrupt, t.faultPartialBytes);
    }

    public DeviceSimulator(UartPort port, int arraySize, TestLog log, String rule, FrameCodec codec,
                           int timeoutMs, int faultDelayMs, boolean faultDrop, String faultCorrupt, int faultPartialBytes) {
        this.port = port;
        this.arraySize = arraySize;
        this.log = log;
        this.deviceRule = rule != null ? rule : "NIBBLE_SWAP";
        this.codec = codec != null ? codec : new FrameCodec();
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 1000;
        this.faultDelayMs = faultDelayMs;
        this.faultDrop = faultDrop;
        this.faultCorrupt = faultCorrupt;
        this.faultPartialBytes = faultPartialBytes;
    }

    @Override
    public void run() {
        int wireSize = codec.wireSize(arraySize);
        while (running) {
            byte[] request = port.read(wireSize, timeoutMs);
            if (request.length == 0) continue;
            if (request.length != wireSize) {
                log.log("DEVICE", "WARN", request, "Incomplete frame discarded (" + request.length + "/" + wireSize + " bytes)");
                continue;
            }
            log.log("DEVICE", "RECEIVE", request, null);
            if (faultDrop) {
                log.log("DEVICE", "WARN", null, "Fault injected: drop reply");
                continue;
            }
            if ("SILENT".equalsIgnoreCase(deviceRule)) continue;
            if (faultDelayMs > 0) {
                log.log("DEVICE", "WARN", null, "Fault injected: delay " + faultDelayMs + " ms");
                try { Thread.sleep(faultDelayMs); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            byte[] payload;
            try {
                payload = codec.decode(request);
            } catch (Exception e) {
                log.log("DEVICE", "WARN", request, "Corrupt request: " + e.getMessage());
                continue;
            }
            byte[] reply = respond(payload, deviceRule);
            byte[] wire = codec.encode(reply);
            if (faultCorrupt != null && faultCorrupt.startsWith("FLIP_BIT:")) {
                try {
                    int bitIdx = Integer.parseInt(faultCorrupt.substring(9).trim());
                    if (bitIdx >= 0 && bitIdx < wire.length) {
                        wire[bitIdx] ^= 0x01;
                        log.log("DEVICE", "WARN", wire, "Fault injected: flip bit at byte " + bitIdx);
                    }
                } catch (NumberFormatException ignored) {}
            }
            if (faultPartialBytes > 0 && faultPartialBytes < wire.length) {
                wire = Arrays.copyOf(wire, faultPartialBytes);
                log.log("DEVICE", "WARN", wire, "Fault injected: partial send " + faultPartialBytes + " bytes");
            }
            port.write(wire);
            log.log("DEVICE", "SEND", wire, null);
        }
    }

    public void stop() { running = false; }

    static byte[] respond(byte[] request) { return DeviceRule.respond(request, "NIBBLE_SWAP"); }

    static byte[] respond(byte[] request, String rule) { return DeviceRule.respond(request, rule); }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: DeviceSimulator <template-file> [device-port]");
            System.exit(2);
        }
        Properties p = new Properties();
        try (java.io.Reader r = Files.newBufferedReader(Paths.get(args[0]), StandardCharsets.UTF_8)) { p.load(r); }
        if (args.length == 2) p.setProperty("port", args[1]);
        Template t = new Template(p, args[0]);
        try (TestLog log = new TestLog(TestLog.deviceLogPath("."))) {
            UartPort port = new SerialUartPort(t);
            System.out.println("Device simulator running on " + t.port + " (" + t.device + "). Press Ctrl+C to stop.");
            new DeviceSimulator(port, t, log).run();
        }
    }
}
