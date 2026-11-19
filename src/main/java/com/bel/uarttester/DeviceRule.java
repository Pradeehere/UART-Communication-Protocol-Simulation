package com.bel.uarttester;

/** Evaluates fake device rules (NIBBLE_SWAP, ECHO, ADD, XOR, FIXED) on incoming payloads. */
public class DeviceRule {
    public static byte[] respond(byte[] request, String rule) {
        String u = rule == null ? "NIBBLE_SWAP" : rule.toUpperCase();
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
}
