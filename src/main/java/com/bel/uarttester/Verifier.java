package com.bel.uarttester;

/** Verifies a received byte array against sent and expected arrays. */
public interface Verifier {
    boolean verify(byte[] sent, byte[] received, byte[] expected);
}
