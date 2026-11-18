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
    public final String name, port, parity, verify, device, source;
    public final int baud, dataBits, stopBits, arraySize, timeoutMs;
    public final byte[] send, expect;

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
        this.expect = parseHex(text(p, "expect"), "expect");
        if (send.length != expect.length || send.length == 0 || send.length % arraySize != 0) {
            throw new IllegalArgumentException(String.format(
                    "Template \"%s\": send (%d) and expect (%d) must be equal positive multiples of arraySize (%d)",
                    source, send.length, expect.length, arraySize));
        }
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

    private byte[] parseHex(String s, String key) {
        try { return parseHex(s); }
        catch (IllegalArgumentException e) { throw err(key, "must be valid 1-2 digit hex bytes (00-FF)", s); }
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes[i]));
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
