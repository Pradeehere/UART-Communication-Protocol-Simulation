package com.bel.uarttester;

/**
 * Encodes/decodes framed UART messages with optional start/end markers,
 * length byte, and checksum (XOR, SUM, CRC8). Keeps framing logic modular
 * and separate from TestRunner and DeviceSimulator.
 */
public class FrameCodec {
    private final int frameStart; // -1 = none
    private final boolean frameLength;
    private final String checksum; // NONE, XOR, SUM, CRC8
    private final int frameEnd; // -1 = none

    public FrameCodec(int frameStart, boolean frameLength, String checksum, int frameEnd) {
        this.frameStart = frameStart;
        this.frameLength = frameLength;
        this.checksum = checksum != null ? checksum.toUpperCase() : "NONE";
        this.frameEnd = frameEnd;
    }

    /** No-framing codec: passes bytes through unchanged. */
    public FrameCodec() {
        this(-1, false, "NONE", -1);
    }

    public boolean isPassthrough() {
        return frameStart < 0 && !frameLength && "NONE".equals(checksum) && frameEnd < 0;
    }

    /** Returns the total wire-frame size for a payload of the given length. */
    public int wireSize(int payloadLen) {
        int n = payloadLen;
        if (frameStart >= 0)
            n++;
        if (frameLength)
            n++;
        if (!"NONE".equals(checksum))
            n++;
        if (frameEnd >= 0)
            n++;
        return n;
    }

    /** Wraps payload bytes into a framed wire message. */
    public byte[] encode(byte[] payload) {
        if (isPassthrough())
            return payload.clone();
        byte[] wire = new byte[wireSize(payload.length)];
        int i = 0;
        if (frameStart >= 0)
            wire[i++] = (byte) frameStart;
        if (frameLength)
            wire[i++] = (byte) payload.length;
        System.arraycopy(payload, 0, wire, i, payload.length);
        i += payload.length;
        if (!"NONE".equals(checksum)) {
            byte cs = computeChecksum(wire, frameStart >= 0 ? 1 : 0, i);
            wire[i++] = cs;
        }
        if (frameEnd >= 0)
            wire[i] = (byte) frameEnd;
        return wire;
    }

    /**
     * Unwraps a framed wire message, returning the payload. Throws on framing
     * errors.
     */
    public byte[] decode(byte[] wire) {
        if (isPassthrough())
            return wire.clone();
        int i = 0;
        if (frameStart >= 0) {
            if (wire.length < 1 || (wire[i] & 0xFF) != frameStart)
                throw new FrameException("Invalid frameStart");
            i++;
        }
        int payloadLen;
        if (frameLength) {
            if (i >= wire.length)
                throw new FrameException("Missing length byte");
            payloadLen = wire[i++] & 0xFF;
        } else {
            int overhead = i + (!"NONE".equals(checksum) ? 1 : 0) + (frameEnd >= 0 ? 1 : 0);
            payloadLen = wire.length - overhead;
        }
        if (i + payloadLen > wire.length)
            throw new FrameException("Frame too short for payload");
        byte[] payload = new byte[payloadLen];
        System.arraycopy(wire, i, payload, 0, payloadLen);
        i += payloadLen;
        if (!"NONE".equals(checksum)) {
            if (i >= wire.length)
                throw new FrameException("Missing checksum byte");
            byte expected = computeChecksum(wire, frameStart >= 0 ? 1 : 0, i);
            if (wire[i] != expected)
                throw new FrameException("Checksum mismatch");
            i++;
        }
        if (frameEnd >= 0) {
            if (i >= wire.length || (wire[i] & 0xFF) != frameEnd)
                throw new FrameException("Invalid frameEnd");
        }
        return payload;
    }

    private byte computeChecksum(byte[] data, int from, int to) {
        switch (checksum) {
            case "XOR":
                return xor(data, from, to);
            case "SUM":
                return sum(data, from, to);
            case "CRC8":
                return crc8(data, from, to);
            default:
                return 0;
        }
    }

    static byte xor(byte[] d, int from, int to) {
        byte r = 0;
        for (int i = from; i < to; i++)
            r ^= d[i];
        return r;
    }

    static byte sum(byte[] d, int from, int to) {
        int r = 0;
        for (int i = from; i < to; i++)
            r += (d[i] & 0xFF);
        return (byte) (r & 0xFF);
    }

    static byte crc8(byte[] d, int from, int to) {
        int crc = 0;
        for (int i = from; i < to; i++) {
            crc ^= (d[i] & 0xFF);
            for (int b = 0; b < 8; b++)
                crc = (crc & 0x80) != 0 ? (crc << 1) ^ 0x07 : crc << 1;
            crc &= 0xFF;
        }
        return (byte) crc;
    }

    /** Unchecked exception for framing/checksum errors during decode. */
    public static class FrameException extends RuntimeException {
        public FrameException(String msg) {
            super(msg);
        }
    }
}
