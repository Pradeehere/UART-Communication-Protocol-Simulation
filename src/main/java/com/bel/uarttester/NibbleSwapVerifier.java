package com.bel.uarttester;

/**
 * Verifier for the 0A -> A0 rule: each byte's two hex digits (nibbles) swap
 * places.
 */
public class NibbleSwapVerifier implements Verifier {
    @Override
    public boolean verify(byte[] sent, byte[] received, byte[] expected) {
        if (received.length != sent.length)
            return false;
        for (int i = 0; i < sent.length; i++) {
            if (received[i] != swap(sent[i]))
                return false;
        }
        return java.util.Arrays.equals(received, expected);
    }

    static byte swap(byte b) {
        int v = b & 0xFF;
        return (byte) (((v & 0x0F) << 4) | (v >> 4));
    }
}