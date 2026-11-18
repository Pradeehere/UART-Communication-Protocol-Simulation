package com.bel.uarttester;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Arrays;

/** UartPort backed by a real or virtual serial port via jSerialComm. */
public class SerialUartPort implements UartPort {
    private final SerialPort port;

    public SerialUartPort(Template t) {
        port = SerialPort.getCommPort(t.port);
        if (!port.setComPortParameters(t.baud, t.dataBits, stopBitsCode(t.stopBits), parityCode(t.parity))) {
            throw new IllegalStateException("Failed to configure serial port parameters for " + t.port);
        }
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 50, 0);
        if (!port.openPort()) {
            String[] available = Arrays.stream(SerialPort.getCommPorts()).map(SerialPort::getSystemPortName).toArray(String[]::new);
            throw new IllegalStateException("Cannot open serial port " + t.port + ". Available ports: " + Arrays.toString(available));
        }
    }

    @Override
    public void write(byte[] data) {
        int written = port.writeBytes(data, data.length);
        if (written == -1) {
            throw new IllegalStateException("Failed to write to port " + port.getSystemPortName());
        }
    }

    @Override
    public byte[] read(int size, int timeoutMs) {
        byte[] received = new byte[size];
        byte[] one = new byte[1];
        int count = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (count < size && System.currentTimeMillis() < deadline) {
            int n = port.readBytes(one, 1);
            if (n == -1) throw new IllegalStateException("Read error on port " + port.getSystemPortName());
            if (n == 1) received[count++] = one[0];
        }
        return Arrays.copyOf(received, count);
    }

    @Override
    public void close() {
        port.closePort();
    }

    private static int stopBitsCode(int stopBits) {
        return stopBits == 2 ? SerialPort.TWO_STOP_BITS : SerialPort.ONE_STOP_BIT;
    }

    private static int parityCode(String parity) {
        switch (parity.toLowerCase()) {
            case "none": return SerialPort.NO_PARITY;
            case "even": return SerialPort.EVEN_PARITY;
            case "odd": return SerialPort.ODD_PARITY;
            default: throw new IllegalArgumentException("Unknown parity: " + parity);
        }
    }
}
