package com.bel.uarttester;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-memory byte transport implementing {@link UartPort} for simulation without
 * OS drivers. A pair of endpoints allows bidirectional, thread-safe communication.
 */
public class LoopbackUartPort implements UartPort {
    private final BlockingQueue<Integer> inbox;
    private final BlockingQueue<Integer> outbox;
    private volatile boolean closed = false;

    private LoopbackUartPort(BlockingQueue<Integer> inbox, BlockingQueue<Integer> outbox) {
        this.inbox = inbox;
        this.outbox = outbox;
    }

    /** Creates a connected pair of in-memory loopback UART endpoints. */
    public static LoopbackUartPort[] pair() {
        BlockingQueue<Integer> q1 = new LinkedBlockingQueue<>();
        BlockingQueue<Integer> q2 = new LinkedBlockingQueue<>();
        LoopbackUartPort a = new LoopbackUartPort(q1, q2);
        LoopbackUartPort b = new LoopbackUartPort(q2, q1);
        return new LoopbackUartPort[]{a, b};
    }

    @Override
    public void write(byte[] bytes) {
        if (closed) {
            throw new IllegalStateException("Port is closed");
        }
        if (bytes == null) {
            return;
        }
        for (byte b : bytes) {
            outbox.offer(b & 0xFF);
        }
    }

    @Override
    public byte[] read(int size, int timeoutMs) {
        if (size <= 0) {
            return new byte[0];
        }
        byte[] received = new byte[size];
        int count = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (count < size && !closed) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            try {
                Integer val = inbox.poll(remaining, TimeUnit.MILLISECONDS);
                if (val == null) {
                    break;
                }
                if (val == -1) {
                    inbox.offer(-1);
                    break;
                }
                received[count++] = (byte) val.intValue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Arrays.copyOf(received, count);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            inbox.offer(-1);
        }
    }

    public boolean isClosed() {
        return closed;
    }
}

