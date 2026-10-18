package io.github.maxmorozov.decimal;

import io.github.maxmorozov.schubfach.Schubfach;

import static io.github.maxmorozov.decimal.DecimalToDouble.asDouble;

public class DecimalConverter {

    public static Decimal encode(double v) {
        DecimalEncoder encoder = new DecimalEncoder();

        int type = Schubfach.encodeDouble(v, encoder);
        return switch (type) {
            case Schubfach.NON_SPECIAL -> encoder.result();
            case Schubfach.PLUS_ZERO -> new Decimal(false, 0, 0, Schubfach.PLUS_ZERO);
            case Schubfach.MINUS_ZERO -> new Decimal(true, 0, 0, Schubfach.MINUS_ZERO);
            case Schubfach.PLUS_INF -> new Decimal(false, Long.MAX_VALUE, Integer.MAX_VALUE, Schubfach.PLUS_INF);
            case Schubfach.MINUS_INF -> new Decimal(true, Long.MAX_VALUE, Integer.MAX_VALUE, Schubfach.MINUS_INF);
            case Schubfach.Q_NAN -> new Decimal(false, 0, Integer.MAX_VALUE, Schubfach.Q_NAN);
            case Schubfach.S_NAN -> new Decimal(false, 1, Integer.MAX_VALUE, Schubfach.S_NAN);
            default -> throw new IllegalStateException("Unexpected type value: " + type);
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
            case Schubfach.NON_SPECIAL -> asDouble(decimal.negative(), decimal.significand(), decimal.exponent());
            case Schubfach.PLUS_ZERO -> 0.0;
            case Schubfach.MINUS_ZERO -> -0.0;
            case Schubfach.PLUS_INF -> Double.POSITIVE_INFINITY;
            case Schubfach.MINUS_INF -> Double.NEGATIVE_INFINITY;
            case Schubfach.Q_NAN, Schubfach.S_NAN -> Double.NaN;
            default -> throw new IllegalStateException("Unexpected type value: " + decimal.type());
        };
    }

}
