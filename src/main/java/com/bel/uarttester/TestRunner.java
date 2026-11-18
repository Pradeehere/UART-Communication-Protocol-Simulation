package com.bel.uarttester;

import java.util.Arrays;
import java.util.Locale;

/**
 * Executes a UART test template by chunking byte arrays, applying framing and checksums,
 * transmitting via UartPort, measuring round-trip latency, and verifying responses.
 */
public class TestRunner {
    private final UartPort port;
    private final Verifier verifier;
    private final TestLog log;
    private final ResultStore store;

    public TestRunner(UartPort port, Verifier verifier, TestLog log, ResultStore store) {
        this.port = port;
        this.verifier = verifier;
        this.log = log;
        this.store = store;
    }

    public boolean run(Template t) {
        FrameBuffer buffer = new FrameBuffer(t.arraySize);
        FrameCodec codec = t.codec();
        int totalArrays = t.send.length / t.arraySize;
        int readWireSize = codec.wireSize(t.arraySize);
        int arrayNo = 1;
        boolean allPassed = true;

        log.log("TESTER", "INFO", null, "Starting test: " + t.name + " (" + totalArrays + " array" + (totalArrays > 1 ? "s" : "") + ")");

        for (byte b : t.send) {
            if (buffer.add(b)) {
                byte[] arrayToSend = buffer.take();
                byte[] wireToSend = codec.encode(arrayToSend);
                int offset = (arrayNo - 1) * t.arraySize;
                byte[] expectedSlice = Arrays.copyOfRange(t.expect, offset, offset + t.arraySize);
                String expectedHex = Template.toExpectHex(t.expectPattern, offset, t.arraySize);

                log.log("TESTER", "SEND", wireToSend, "Array " + arrayNo + "/" + totalArrays);

                long startNano = System.nanoTime();
                port.write(wireToSend);
                byte[] rawReply = port.read(readWireSize, t.timeoutMs);
                long endNano = System.nanoTime();
                double latencyMs = (endNano - startNano) / 1_000_000.0;

                log.log("TESTER", "RECEIVE", rawReply, "Array " + arrayNo + "/" + totalArrays);

                byte[] receivedPayload = null;
                String failReason = null;
                if (rawReply.length != readWireSize) {
                    failReason = "expected " + expectedHex;
                } else {
                    try {
                        receivedPayload = codec.decode(rawReply);
                    } catch (FrameCodec.FrameException e) {
                        failReason = e.getMessage();
                    }
                }

                boolean pass = false;
                if (failReason == null && receivedPayload != null && receivedPayload.length == t.arraySize) {
                    if (verifier instanceof ExactMatchVerifier emv) {
                        pass = emv.verifyPattern(receivedPayload, t.expectPattern, offset, t.arraySize);
                    } else {
                        pass = verifier.verify(arrayToSend, receivedPayload, expectedSlice);
                    }
                    if (!pass) failReason = "expected " + expectedHex;
                }
                if (!pass) allPassed = false;

                String lat = String.format(Locale.US, "%.2f", latencyMs);
                String verdict = pass ? "PASS (" + lat + " ms)" : "FAIL (" + failReason + ") (" + lat + " ms)";
                byte[] logBytes = receivedPayload != null ? receivedPayload : rawReply;
                log.log("TESTER", "RESULT", logBytes, "Array " + arrayNo + ": " + verdict);

                if (store != null) {
                    try {
                        store.save(t.name, arrayNo, arrayToSend, logBytes, expectedHex, latencyMs, pass);
                    } catch (Exception e) {
                        log.log("TESTER", "ERROR", null, "Failed to persist array " + arrayNo + " result: " + e.getMessage());
                    }
                }
                arrayNo++;
            }
        }
        log.log("TESTER", "RESULT", null, allPassed ? "ALL ARRAYS PASSED" : "TEST FAILED");
        return allPassed;
    }

    public static Verifier verifierFor(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Verifier name cannot be empty");
        String clean = name.trim();
        if ("NIBBLE_SWAP".equalsIgnoreCase(clean)) return new NibbleSwapVerifier();
        if ("EXACT_MATCH".equalsIgnoreCase(clean)) return new ExactMatchVerifier();
        try {
            Class<?> clazz = Class.forName(clean);
            if (!Verifier.class.isAssignableFrom(clazz)) throw new IllegalArgumentException("Class " + clean + " does not implement Verifier");
            return (Verifier) clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unknown verifier: " + clean, e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Verifier class " + clean + " must have a public no-arg constructor", e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot instantiate verifier " + clean + ": " + e.getMessage(), e);
        }
    }
}
