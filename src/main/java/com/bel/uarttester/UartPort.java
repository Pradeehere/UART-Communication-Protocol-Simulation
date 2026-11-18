package com.bel.uarttester;

/** Defines byte-array reading, writing, and closing for a UART port. */
public interface UartPort extends AutoCloseable {
    void write(byte[] bytes);

    byte[] read(int size, int timeoutMs);

    @Override
    void close();
}
