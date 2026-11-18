package com.bel.uarttester;

/**
 * Verifier that passes only when the received array equals the expected array.
 */
public class ExactMatchVerifier implements Verifier {
    @Override
    public boolean verify(byte[] sent, byte[] received, byte[] expected) {
        return java.util.Arrays.equals(received, expected);
    }
}