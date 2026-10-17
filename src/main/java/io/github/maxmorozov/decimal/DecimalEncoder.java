package io.github.maxmorozov.decimal;

import io.github.maxmorozov.schubfach.Schubfach.DoubleEncoder;

public class DecimalEncoder implements DoubleEncoder {
    private Decimal result;

    @Override
    public void encodeDouble(boolean negative, long significand, int exponent) {
        result = new Decimal(negative, significand, exponent, 0);
    }

    public Decimal result() {
        return result;
    }


}
