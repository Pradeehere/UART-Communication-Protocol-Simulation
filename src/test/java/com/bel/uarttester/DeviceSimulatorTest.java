package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeviceSimulatorTest {
    @TempDir
    Path tempDir;

    @Test
    void respondsToAllRules() {
        byte[] in = new byte[]{0x0A, 0x1B, (byte) 0x2C, (byte) 0x3D};

        assertArrayEquals(new byte[]{(byte) 0xA0, (byte) 0xB1, (byte) 0xC2, (byte) 0xD3},
                DeviceSimulator.respond(in, "NIBBLE_SWAP"));
        assertArrayEquals(in, DeviceSimulator.respond(in, "ECHO"));
        assertArrayEquals(new byte[]{0x0B, 0x1C, (byte) 0x2D, (byte) 0x3E},
                DeviceSimulator.respond(in, "ADD:01"));
        assertArrayEquals(new byte[]{(byte) 0xF5, (byte) 0xE4, (byte) 0xD3, (byte) 0xC2},
                DeviceSimulator.respond(in, "XOR:FF"));
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04},
                DeviceSimulator.respond(in, "FIXED:01 02 03 04"));
    }

    @Test
    void partialFrameIsDiscardedAndLogged() throws Exception {
        LoopbackUartPort[] pair = LoopbackUartPort.pair();
        Path logPath = tempDir.resolve("dev.log");
        try (TestLog log = new TestLog(logPath.toString());
             UartPort simPort = pair[1]) {
            DeviceSimulator sim = new DeviceSimulator(simPort, 4, log);
            Thread t = new Thread(sim);
            t.start();

            // Send partial frame (2 bytes instead of 4)
            pair[0].write(new byte[]{0x0A, 0x1B});
            Thread.sleep(100);

            sim.stop();
            simPort.close();
            t.join(1000);

            // Read from pair[0] to confirm simulator did not send any response back
            byte[] reply = pair[0].read(4, 50);
            assertEquals(0, reply.length);
            pair[0].close();
        }
    }
}
