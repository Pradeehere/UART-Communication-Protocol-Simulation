package com.bel.uarttester;

import java.util.Arrays;

/**
 * Verifies that the received array matches expected bytes, supporting wildcard ?? tokens.
 */
public class ExactMatchVerifier implements Verifier {
    @Override
    public boolean verify(byte[] sent, byte[] received, byte[] expected) {
        return Arrays.equals(received, expected);
    }

    /** Matches received bytes against an expect pattern where -1 denotes a wildcard. */
    public boolean verifyPattern(byte[] received, int[] pattern, int offset, int len) {
        if (received == null || received.length != len) return false;
        for (int i = 0; i < len; i++) {
            int p = pattern[offset + i];
            if (p != -1 && (received[i] & 0xFF) != p) return false;
        }
        return true;
    }
}
