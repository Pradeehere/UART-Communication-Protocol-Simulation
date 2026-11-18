package com.bel.uarttester;

import java.util.Arrays;

/**
 * Executes a template by chunking send/expect bytes through a FrameBuffer,
 * transmitting fixed-size arrays over UART, verifying replies, and persisting results.
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
        int totalArrays = t.send.length / t.arraySize;
        int arrayNo = 1;
        boolean allPassed = true;

        log.log("TESTER", "INFO", null, "Starting test: " + t.name + " (" + totalArrays + " array" + (totalArrays > 1 ? "s" : "") + ")");

        for (byte b : t.send) {
            if (buffer.add(b)) {
                byte[] arrayToSend = buffer.take();
                int offset = (arrayNo - 1) * t.arraySize;
                byte[] expectedSlice = Arrays.copyOfRange(t.expect, offset, offset + t.arraySize);

                log.log("TESTER", "SEND", arrayToSend, "Array " + arrayNo + "/" + totalArrays);
                port.write(arrayToSend);

                byte[] received = port.read(t.arraySize, t.timeoutMs);
                log.log("TESTER", "RECEIVE", received, "Array " + arrayNo + "/" + totalArrays);

                boolean pass = received.length == t.arraySize && verifier.verify(arrayToSend, received, expectedSlice);
                if (!pass) allPassed = false;

                String verdict = pass ? "PASS" : "FAIL (expected " + Template.toHex(expectedSlice) + ")";
                log.log("TESTER", "RESULT", received, "Array " + arrayNo + ": " + verdict);

                if (store != null) {
                    try {
                        store.save(t.name, arrayNo, arrayToSend, received, expectedSlice, pass);
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
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Verifier name cannot be empty");
        }
        String clean = name.trim();
        if ("NIBBLE_SWAP".equalsIgnoreCase(clean)) return new NibbleSwapVerifier();
        if ("EXACT_MATCH".equalsIgnoreCase(clean)) return new ExactMatchVerifier();
        try {
            Class<?> clazz = Class.forName(clean);
            if (!Verifier.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("Class " + clean + " does not implement Verifier");
            }
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
