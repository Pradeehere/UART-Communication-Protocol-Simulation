package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameCodecTest {

    @Test
    void passthroughCodecLeavesBytesUntouched() {
        FrameCodec codec = new FrameCodec();
        assertTrue(codec.isPassthrough());
        byte[] payload = new byte[]{0x12, 0x34, 0x56};
        assertArrayEquals(payload, codec.encode(payload));
        assertArrayEquals(payload, codec.decode(payload));
        assertEquals(3, codec.wireSize(3));
    }

    @Test
    void xorChecksumCalculationAndCorruption() {
        byte[] data = new byte[]{0x10, 0x20, 0x30, 0x40};
        byte expectedXor = (byte) (0x10 ^ 0x20 ^ 0x30 ^ 0x40);
        assertEquals(expectedXor, FrameCodec.xor(data, 0, data.length));

        FrameCodec codec = new FrameCodec(-1, false, "XOR", -1);
        byte[] wire = codec.encode(data);
        assertEquals(5, wire.length);
        assertEquals(expectedXor, wire[4]);
        assertArrayEquals(data, codec.decode(wire));

        // Corrupt checksum byte
        wire[4] ^= 0x01;
        assertThrows(FrameCodec.FrameException.class, () -> codec.decode(wire));
    }

    @Test
    void sumChecksumCalculationAndCorruption() {
        byte[] data = new byte[]{0x50, 0x60, 0x70, (byte) 0x80};
        byte expectedSum = (byte) ((0x50 + 0x60 + 0x70 + 0x80) & 0xFF);
        assertEquals(expectedSum, FrameCodec.sum(data, 0, data.length));

        FrameCodec codec = new FrameCodec(-1, false, "SUM", -1);
        byte[] wire = codec.encode(data);
        assertEquals(5, wire.length);
        assertEquals(expectedSum, wire[4]);
        assertArrayEquals(data, codec.decode(wire));

        // Corrupt data byte
        wire[1] ^= 0x02;
        assertThrows(FrameCodec.FrameException.class, () -> codec.decode(wire));
    }

    @Test
    void crc8ChecksumCalculationAndCorruption() {
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04};
        byte crc = FrameCodec.crc8(data, 0, data.length);
        FrameCodec codec = new FrameCodec(-1, false, "CRC8", -1);
        byte[] wire = codec.encode(data);
        assertEquals(5, wire.length);
        assertEquals(crc, wire[4]);
        assertArrayEquals(data, codec.decode(wire));

        // Corrupt any byte
        wire[0] ^= 0xFF;
        assertThrows(FrameCodec.FrameException.class, () -> codec.decode(wire));
    }

    @Test
    void fullFramingRoundTripAndMarkers() {
        // start=0xAA, length=true, checksum=CRC8, end=0x55
        FrameCodec codec = new FrameCodec(0xAA, true, "CRC8", 0x55);
        assertFalse(codec.isPassthrough());
        byte[] payload = new byte[]{0x2A, 0x3B, 0x4C, 0x5D};
        // wireSize = 1(start) + 1(len) + 4(payload) + 1(crc8) + 1(end) = 8
        assertEquals(8, codec.wireSize(payload.length));

        byte[] wire = codec.encode(payload);
        assertEquals((byte) 0xAA, wire[0]);
        assertEquals(4, wire[1]);
        assertEquals((byte) 0x55, wire[7]);

        byte[] decoded = codec.decode(wire);
        assertArrayEquals(payload, decoded);

        // Invalid start marker
        byte[] badStart = wire.clone();
        badStart[0] = 0x11;
        assertThrows(FrameCodec.FrameException.class, () -> codec.decode(badStart));

        // Invalid end marker
        byte[] badEnd = wire.clone();
        badEnd[7] = 0x22;
        assertThrows(FrameCodec.FrameException.class, () -> codec.decode(badEnd));
    }
}
