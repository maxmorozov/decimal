package io.github.maxmorozov.decimal9;

import io.github.maxmorozov.schubfach.Schubfach;
import io.github.maxmorozov.schubfach.Schubfach.EncodingResult;

import static io.github.maxmorozov.decimal9.RoundingDouble.flipSign;
import static io.github.maxmorozov.decimal9.RoundingDouble.scaleToDouble;

public final class Decimal9Converter {
    private static final int NANOS_PER_UNIT = 1_000_000_000;

    // Every finite double below 2^63 renders to at most Long.MAX_VALUE units; 2^63 renders as 9.223372036854776E18.
    private static final double MAX_ABS_EXCLUSIVE = 0x1p63;

    private Decimal9Converter() {}

    /**
     * Converts a {@code double} to a {@link Decimal9}: the shortest decimal that uniquely identifies {@code v}, rounded
     * half-up to 9 fractional digits. Values below half a nano in magnitude become zero, losing the sign.
     *
     * @param v the value to convert
     * @return the {@link Decimal9} representation of {@code v}
     * @throws IllegalArgumentException if {@code v} is NaN, infinite, or its magnitude is at least 2^63
     */
    public static Decimal9 fromDouble(double v) {
        // NaN is handled by Schubfach and rejected in getDecimal().
        if (Math.abs(v) >= MAX_ABS_EXCLUSIVE) {
            throw new IllegalArgumentException("Value is not representable as Decimal: " + v);
        }

        DecimalEncoder encoder = new DecimalEncoder();

        EncodingResult type = Schubfach.encodeDouble(v, encoder);
        return getDecimal(encoder, type);
    }

    /**
     * Converts a {@link Decimal9} to the nearest {@code double} (round-half-even).
     *
     * @param value the {@link Decimal9} value to convert
     * @return the double representation of the {@link Decimal9} value
     */
    public static double toDouble(Decimal9 value) {
        long units = value.units();
        long nanos = value.nanos();

        // Handle Long.MIN_VALUE separately, because we will work with positive significand,
        // but -Long.MIN_VALUE does not fit into 64 bits
        if (units == Long.MIN_VALUE) {
            // At this magnitude ULP is far larger than 1, so nanos cannot change the result.
            return units;
        }

        if (nanos == 0) {
            return units;
        }

        long sign = (units | nanos) >> 63;                 // 0 or -1
        units  = flipSign(units, sign);
        nanos = flipSign(nanos, sign);

        // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
        // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
        long lo1 = units * NANOS_PER_UNIT;
        long lo = lo1 + nanos;
        long hi = Math.multiplyHigh(units, NANOS_PER_UNIT); // units >= 0: signed == unsigned
        if (Long.compareUnsigned(lo, lo1) < 0) {
            ++hi;
        }

        return scaleToDouble(hi, lo, sign);
    }

    private static Decimal9 getDecimal(DecimalEncoder encoder, EncodingResult type) {
        return switch (type) {
            case NON_SPECIAL -> encoder.result();
            case PLUS_ZERO, MINUS_ZERO -> new Decimal9(0, 0);
            case NAN -> throw new IllegalArgumentException("Value is not representable as Decimal: NaN");
            case PLUS_INF, MINUS_INF -> throw new IllegalStateException("Infinite value passed the range check");
        };
    }
}
