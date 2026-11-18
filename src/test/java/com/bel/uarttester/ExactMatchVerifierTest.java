package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExactMatchVerifierTest {
    @Test
    void verifiesExactMatches() {
        byte[] sent = new byte[]{0x01, 0x02};
        byte[] match = new byte[]{0x01, 0x02};
        byte[] mismatch = new byte[]{0x01, 0x03};

        ExactMatchVerifier verifier = new ExactMatchVerifier();
        assertTrue(verifier.verify(sent, match, match));
        assertFalse(verifier.verify(sent, mismatch, match));
    }
}

