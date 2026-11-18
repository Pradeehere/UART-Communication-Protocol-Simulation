package com.bel.uarttester;

import java.util.Arrays;

/** Custom Verifier demonstrating dynamic FQCN reflection loading without core edits. */
public class SampleCustomVerifier implements Verifier {
    @Override
    public boolean verify(byte[] sent, byte[] received, byte[] expected) {
        return Arrays.equals(received, expected);
    }
}

