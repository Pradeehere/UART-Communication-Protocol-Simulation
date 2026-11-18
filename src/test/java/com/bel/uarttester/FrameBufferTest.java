package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameBufferTest {
    @Test
    void fillsAtTheRightSize() {
        FrameBuffer buffer = new FrameBuffer(2);
        assertFalse(buffer.add((byte) 0x0A));
        assertEquals(1, buffer.size());
        assertTrue(buffer.add((byte) 0x1B));
        assertEquals(2, buffer.size());
    }

    @Test
    void takeResets() {
        FrameBuffer buffer = new FrameBuffer(2);
        buffer.add((byte) 0x0A);
        buffer.add((byte) 0x1B);
        assertArrayEquals(new byte[]{0x0A, 0x1B}, buffer.take());
        assertEquals(0, buffer.size());
        assertThrows(IllegalStateException.class, buffer::take);
    }
}
