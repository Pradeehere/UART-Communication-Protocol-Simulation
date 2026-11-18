package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NibbleSwapVerifierTest {
    @Test
    void acceptsNibbleSwappedBytes() {
        assertTrue(new NibbleSwapVerifier().verify(
                new byte[]{0x0A, 0x1B}, new byte[]{(byte) 0xA0, (byte) 0xB1},
                new byte[]{(byte) 0xA0, (byte) 0xB1}));
    }

    @Test
    void rejectsWrongReply() {
        assertFalse(new NibbleSwapVerifier().verify(
                new byte[]{0x0A, 0x1B}, new byte[]{(byte) 0xA0, (byte) 0x00},
                new byte[]{(byte) 0xA0, (byte) 0xB1}));
    }
}
