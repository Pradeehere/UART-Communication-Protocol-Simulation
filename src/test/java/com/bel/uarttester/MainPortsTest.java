package com.bel.uarttester;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fazecast.jSerialComm.SerialPort;
import org.junit.jupiter.api.Test;

class MainPortsTest {
    @Test
    void queriesCommPortsWithoutError() {
        SerialPort[] ports = SerialPort.getCommPorts();
        assertNotNull(ports);
        for (SerialPort p : ports) {
            assertNotNull(p.getSystemPortName());
        }
    }
}
