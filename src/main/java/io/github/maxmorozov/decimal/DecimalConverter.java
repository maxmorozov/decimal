package io.github.maxmorozov.decimal;

import io.github.maxmorozov.schubfach.Schubfach;

public class DecimalConverter {

    private static final double[] DOUBLE_POW10 = {
            1e0,  1e1,  1e2,  1e3,  1e4,  1e5,  1e6,  1e7,  1e8,  1e9,  1e10, 1e11,
            1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
    };

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

    private static double asDouble(boolean negative, long sig, int exp) {
        double v;
        if (sig <= (1L << 53) && exp >= -22 && exp <= 22) {
            // Fast path (Clinger): both operands are exactly representable as
            // double, so a single IEEE operation yields a correctly rounded result.
            v = exp >= 0 ? (double) sig * DOUBLE_POW10[exp]
                    : (double) sig / DOUBLE_POW10[-exp];
        } else {
            // General path: exact big-integer arithmetic, no strings.
            v = DecimalToDouble.compute_float(sig, exp);
        }
        return negative ? -v: v;
    }

}
