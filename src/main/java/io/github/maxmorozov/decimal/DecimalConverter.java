package io.github.maxmorozov.decimal;

import io.github.maxmorozov.schubfach.Schubfach;
import io.github.maxmorozov.schubfach.Schubfach.EncodingResult;

import static io.github.maxmorozov.decimal.DecimalToDouble.asDouble;

public class DecimalConverter {

    public static Decimal encode(double v) {
        DecimalEncoder encoder = new DecimalEncoder();

        EncodingResult type = Schubfach.encodeDouble(v, encoder);
        return switch (type) {
            case NON_SPECIAL -> encoder.result();
            case PLUS_ZERO -> new Decimal(false, 0, 0, EncodingResult.PLUS_ZERO);
            case MINUS_ZERO -> new Decimal(true, 0, 0, EncodingResult.MINUS_ZERO);
            case PLUS_INF -> new Decimal(false, Long.MAX_VALUE, Integer.MAX_VALUE, EncodingResult.PLUS_INF);
            case MINUS_INF -> new Decimal(true, Long.MAX_VALUE, Integer.MAX_VALUE, EncodingResult.MINUS_INF);
            case NAN -> new Decimal(false, 0, Integer.MAX_VALUE, EncodingResult.NAN);
        };
    }

    /**
     * Converts a decimal value into a standard Java double.
     *
     * @param decimal decimal value
     * @return The corresponding double value
     */
    public static double toDouble(Decimal decimal) {
        return switch (decimal.type()) {
            case NON_SPECIAL -> asDouble(decimal.negative(), decimal.significand(), decimal.exponent());
            case PLUS_ZERO -> 0.0;
            case MINUS_ZERO -> -0.0;
            case PLUS_INF -> Double.POSITIVE_INFINITY;
            case MINUS_INF -> Double.NEGATIVE_INFINITY;
            case NAN -> Double.NaN;
        };
    }

}
