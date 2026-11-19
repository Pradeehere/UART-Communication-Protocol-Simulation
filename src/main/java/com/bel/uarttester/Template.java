package com.bel.uarttester;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;

/** Loads and validates a UART test definition from a .properties file. */
public class Template {
    private static final Set<String> PARITIES = Set.of("none", "even", "odd");
    private static final Set<String> CHECKSUMS = Set.of("NONE", "XOR", "SUM", "CRC8");

    public final String name, port, parity, verify, device, source, expectDisplay;
    public final int baud, dataBits, stopBits, arraySize, timeoutMs;
    public final byte[] send, expect;
    public final int[] expectPattern; // -1 = wildcard ??

    // Framing & checksum
    public final int frameStart, frameEnd; // -1 = none
    public final boolean frameLength;
    public final String checksum;

    // Fault injection & timing
    public final int faultDelayMs, faultPartialBytes;
    public final boolean faultDrop, baudTiming;
    public final String faultCorrupt; // NONE or FLIP_BIT:<index>

    public Template(Properties p) { this(p, "template"); }

    public Template(Properties p, String src) {
        this.source = src;
        this.name = p.getProperty("name", defaultName(src)).trim();
        this.port = text(p, "port");
        this.parity = text(p, "parity").toLowerCase();
        if (!PARITIES.contains(parity)) throw err("parity", "must be one of [none, even, odd]", parity);
        this.verify = text(p, "verify");
        TestRunner.verifierFor(verify);
        this.baud = posInt(p, "baud");
        this.dataBits = allowed(p, "dataBits", Set.of(5, 6, 7, 8));
        this.stopBits = allowed(p, "stopBits", Set.of(1, 2));
        this.arraySize = p.containsKey("arraySize") ? posInt(p, "arraySize") : 8;
        this.timeoutMs = posInt(p, "timeoutMs");
        this.device = checkDev(p.getProperty("device", "NIBBLE_SWAP").trim());
        this.send = parseHex(text(p, "send"), "send");
        this.expectDisplay = text(p, "expect");
        this.expectPattern = parseExpectPattern(expectDisplay, "expect", source);
        this.expect = expectBytes();
        if (send.length != expectPattern.length || send.length == 0 || send.length % arraySize != 0)
            throw new IllegalArgumentException(String.format(
                    "Template \"%s\": send (%d) and expect (%d) must be equal positive multiples of arraySize (%d)",
                    source, send.length, expectPattern.length, arraySize));

        this.frameStart = optHexByte(p, "frameStart");
        this.frameEnd = optHexByte(p, "frameEnd");
        this.frameLength = "true".equalsIgnoreCase(p.getProperty("frameLength", "false").trim());
        if (frameLength && arraySize > 255)
            throw new IllegalArgumentException(String.format(
                    "Template \"%s\": key \"arraySize\" cannot exceed 255 when \"frameLength\" is true, got \"%d\"",
                    source, arraySize));
        String cs = p.getProperty("checksum", "NONE").trim().toUpperCase();
        if (!CHECKSUMS.contains(cs)) throw err("checksum", "must be one of " + CHECKSUMS, cs);
        this.checksum = cs;

        this.faultDelayMs = optNonNegInt(p, "faultDelayMs");
        this.faultPartialBytes = optNonNegInt(p, "faultPartialBytes");
        this.faultDrop = "true".equalsIgnoreCase(p.getProperty("faultDrop", "false").trim());
        String fc = p.getProperty("faultCorrupt", "NONE").trim().toUpperCase();
        if (!fc.equals("NONE") && !fc.startsWith("FLIP_BIT:")) throw err("faultCorrupt", "must be NONE or FLIP_BIT:<index>", fc);
        this.faultCorrupt = fc;
        this.baudTiming = "true".equalsIgnoreCase(p.getProperty("baudTiming", "false").trim());
    }

    public FrameCodec codec() { return new FrameCodec(frameStart, frameLength, checksum, frameEnd); }

    public double roundTripWireTimeMs() {
        int wire = codec().wireSize(arraySize);
        int parityBit = "none".equalsIgnoreCase(parity) ? 0 : 1;
        int bitsPerByte = 1 + dataBits + parityBit + stopBits;
        return 2.0 * wire * bitsPerByte * 1000.0 / baud;
    }

    public byte[] expectBytes() {
        byte[] out = new byte[expectPattern.length];
        for (int i = 0; i < expectPattern.length; i++)
            out[i] = expectPattern[i] < 0 ? 0 : (byte) expectPattern[i];
        return out;
    }

    public static Template load(String path) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) { p.load(r); }
        return new Template(p, path);
    }

    public static byte[] parseHex(String s) {
        String[] parts = s.trim().split("\\s+");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("^[0-9a-fA-F]{1,2}$")) throw new IllegalArgumentException("Invalid hex byte: " + parts[i]);
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    static int[] parseExpectPattern(String s, String key, String source) {
        String[] parts = s.trim().split("\\s+");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if ("??".equals(parts[i])) { out[i] = -1; continue; }
            if (!parts[i].matches("^[0-9a-fA-F]{1,2}$"))
                throw new IllegalArgumentException(String.format(
                        "Template \"%s\": key \"%s\" must be valid 1-2 digit hex bytes (00-FF) or ??, got \"%s\"",
                        source, key, s));
            out[i] = Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    private byte[] parseHex(String s, String key) {
        try { return parseHex(s); }
        catch (IllegalArgumentException e) { throw err(key, "must be valid 1-2 digit hex bytes (00-FF)", s); }
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public static String toHex(byte[] bytes) {
        return toHex(bytes, Integer.MAX_VALUE);
    }

    public static String toHex(byte[] bytes, int maxBytes) {
        if (bytes == null || bytes.length == 0) return "";
        int len = Math.min(bytes.length, maxBytes);
        StringBuilder sb = new StringBuilder(len * 3 + 32);
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(' ');
            byte b = bytes[i];
            sb.append(HEX[(b >>> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        if (bytes.length > maxBytes) {
            sb.append(" ... (").append(bytes.length).append(" bytes)");
        }
        return sb.toString();
    }

    public static String toExpectHex(int[] pattern, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = off; i < off + len; i++) {
            if (i > off) sb.append(' ');
            if (pattern[i] < 0) sb.append("??");
            else {
                int b = pattern[i];
                sb.append(HEX[(b >>> 4) & 0xF]).append(HEX[b & 0xF]);
            }
        }
        return sb.toString();
    }

    private String text(Properties p, String key) {
        String val = p.getProperty(key);
        if (val == null || val.trim().isEmpty()) throw new IllegalArgumentException("Template \"" + source + "\": key \"" + key + "\" is missing");
        return val.trim();
    }

    private int posInt(Properties p, String key) {
        try {
            int v = Integer.parseInt(text(p, key));
            if (v <= 0) throw err(key, "must be a positive integer", String.valueOf(v));
            return v;
        } catch (NumberFormatException e) { throw err(key, "must be a positive integer", text(p, key)); }
    }

    private int optNonNegInt(Properties p, String key) {
        String v = p.getProperty(key, "0").trim();
        try { int n = Integer.parseInt(v); if (n < 0) throw err(key, "must be >= 0", v); return n; }
        catch (NumberFormatException e) { throw err(key, "must be a non-negative integer", v); }
    }

    private int optHexByte(Properties p, String key) {
        String v = p.getProperty(key, "").trim();
        if (v.isEmpty()) return -1;
        if (!v.matches("^[0-9a-fA-F]{1,2}$")) throw err(key, "must be a valid hex byte (00-FF)", v);
        return Integer.parseInt(v, 16);
    }

    private int allowed(Properties p, String key, Set<Integer> ok) {
        int v = posInt(p, key);
        if (!ok.contains(v)) throw err(key, "must be in " + ok, String.valueOf(v));
        return v;
    }

    private String checkDev(String d) {
        String u = d.toUpperCase();
        if (u.equals("NIBBLE_SWAP") || u.equals("ECHO") || u.equals("SILENT")) return d;
        if ((u.startsWith("ADD:") || u.startsWith("XOR:")) && d.substring(4).trim().matches("^[0-9a-fA-F]{1,2}$")) return d;
        if (u.startsWith("FIXED:") && parseHex(d.substring(6).trim(), "device").length == arraySize) return d;
        throw err("device", "must be NIBBLE_SWAP, ECHO, ADD:<hex>, XOR:<hex>, or FIXED:<hex bytes>", d);
    }

    private IllegalArgumentException err(String key, String reason, String got) {
        return new IllegalArgumentException(String.format("Template \"%s\": key \"%s\" %s, got \"%s\"", source, key, reason, got));
    }

    private static String defaultName(String path) {
        String fn = Paths.get(path).getFileName().toString();
        int dot = fn.lastIndexOf('.');
        return dot > 0 ? fn.substring(0, dot) : fn;
    }
}
