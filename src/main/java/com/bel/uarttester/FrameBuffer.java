package com.bel.uarttester;

/**
 * Collects bytes into a fixed-size array; when full, hands it out and starts a
 * new empty one.
 */
public class FrameBuffer {
    private final byte[] data;
    private int count = 0;

    public FrameBuffer(int size) {
        if (size <= 0)
            throw new IllegalArgumentException("size must be positive");
        data = new byte[size];
    }

    /** Adds one byte. Returns true when the array has just become full. */
    public boolean add(byte b) {
        data[count++] = b;
        return count == data.length;
    }

    /** Returns the completed array and resets the buffer for the next one. */
    public byte[] take() {
        if (count != data.length)
            throw new IllegalStateException("buffer is not full yet");
        byte[] done = data.clone();
        count = 0;
        return done;
    }

    public int size() {
        return count;
    }
}