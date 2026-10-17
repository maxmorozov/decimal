package io.github.maxmorozov.decimal64;

import io.github.maxmorozov.schubfach.Schubfach.DoubleEncoder;

public class Decimal64Encoder implements DoubleEncoder {
    private long result;

    @Override
    public void encodeDouble(boolean negative, long significand, int exponent) {
        result = Decimal64.encode(negative, significand, exponent);
    }

    public long result() {
        return result;
    }


}
