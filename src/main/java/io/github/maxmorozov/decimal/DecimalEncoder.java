package io.github.maxmorozov.decimal;

import io.github.maxmorozov.schubfach.Schubfach.DoubleEncoder;
import io.github.maxmorozov.schubfach.Schubfach.EncodingResult;

public class DecimalEncoder implements DoubleEncoder {
    private Decimal result;

    @Override
    public void encodeDouble(boolean negative, long significand, int exponent) {
        result = new Decimal(negative, significand, exponent, EncodingResult.NON_SPECIAL);
    }

    public Decimal result() {
        return result;
    }


}
