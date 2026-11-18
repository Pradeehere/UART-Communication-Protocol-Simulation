package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LoopbackUartPortTest {
    @Test
    void pairConnectsBidirectionally() {
        LoopbackUartPort[] pair = LoopbackUartPort.pair();
        LoopbackUartPort a = pair[0];
        LoopbackUartPort b = pair[1];

        byte[] sentFromA = new byte[]{0x01, (byte) 0x80, (byte) 0xFF};
        a.write(sentFromA);
        assertArrayEquals(sentFromA, b.read(3, 500));

        byte[] sentFromB = new byte[]{0x10, 0x20};
        b.write(sentFromB);
        assertArrayEquals(sentFromB, a.read(2, 500));

        a.close();
        b.close();
    }

    @Test
    void readTimesOutWhenNoData() {
        LoopbackUartPort[] pair = LoopbackUartPort.pair();
        long start = System.currentTimeMillis();
        byte[] read = pair[1].read(4, 100);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(0, read.length);
        assertTrue(elapsed >= 80, "Expected timeout around 100ms, took " + elapsed + "ms");
        pair[0].close();
        pair[1].close();
    }

    @Test
    void readReturnsPartialDataOnTimeout() {
        LoopbackUartPort[] pair = LoopbackUartPort.pair();
        pair[0].write(new byte[]{0x0A, 0x1B});
        byte[] read = pair[1].read(4, 100);
        assertArrayEquals(new byte[]{0x0A, 0x1B}, read);
        pair[0].close();
        pair[1].close();
    }

    @Test
    void closeUnblocksBlockedReader() throws Exception {
        LoopbackUartPort[] pair = LoopbackUartPort.pair();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<byte[]> result = new AtomicReference<>();

        Thread t = new Thread(() -> {
            started.countDown();
            result.set(pair[1].read(4, 3000));
        });
        t.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(50);
        pair[1].close();
        t.join(1000);

        assertTrue(!t.isAlive(), "Reader thread should terminate when closed");
        assertEquals(0, result.get().length);
        pair[0].close();
    }

    @Test
    void writeThrowsWhenClosed() {
        LoopbackUartPort[] pair = LoopbackUartPort.pair();
        pair[0].close();
        assertThrows(IllegalStateException.class, () -> pair[0].write(new byte[]{0x01}));
        pair[1].close();
    }
}

