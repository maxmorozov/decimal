package io.github.maxmorozov.fpdecimal;

import io.github.maxmorozov.bigmath.FPRounding;
import io.github.maxmorozov.bigmath.FPRoundingDouble;
import io.github.maxmorozov.decimal.Decimal;
import io.github.maxmorozov.decimal.DecimalToDouble;

public class FPDecimalConverter {
    private static final int NANOS_PER_UNIT = 1_000_000_000;

    private static final long FAST_MAX_SIG_D = 1L << 53;   // integers up to 2^53 are exact in double
    private static final long FAST_MAX_SIG_F = 1L << 24;   // ... and up to 2^24 in float

    private FPDecimalConverter() {}

    /**
     * Converts a {@link Decimal} to the nearest {@code double} (round-half-even).
     *
     * <p>A Decimal received from a client is client input: a service should catch the {@link IllegalArgumentException}
     * and fail the call with {@code INVALID_ARGUMENT}, otherwise the framework reports it as {@code UNKNOWN}.
     *
     * @param value the Decimal value to convert
     * @return the double representation of the Decimal value
     * @throws IllegalArgumentException if {@code units} and {@code nanos} have different signs, or {@code nanos} is
     *     outside [-999,999,999; 999,999,999]
     */
    public static double toDoubleV1(FPDecimal value) {
        long units = value.units();
        int nanos = value.nanos();

        // Handle Long.MIN_VALUE separately, because we will work with positive significand,
        // but -Long.MIN_VALUE does not fit into 64 bits
        if (units == Long.MIN_VALUE) {
            // At this magnitude ULP is far larger than 1, so nanos cannot change the result.
            return units;
        }

        if (nanos == 0) {
            return value.negative() ? -(double)units: units;
        } else {
            // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
            // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
            long lo1 = units * NANOS_PER_UNIT;
            long lo = lo1 + nanos;
            long hi = Math.multiplyHigh(units, NANOS_PER_UNIT); // units >= 0: signed == unsigned
            if (Long.compareUnsigned(lo, lo1) < 0) {
                ++hi;
            }

            double v = scaleToDoubleV1(hi, lo);

            return value.negative() ? -v : v;
        }
    }

    public static double toDoubleV3(FPDecimal value) {
        long units = value.units();
        int nanos = value.nanos();

        // Handle Long.MIN_VALUE separately, because we will work with positive significand,
        // but -Long.MIN_VALUE does not fit into 64 bits
        if (units == Long.MIN_VALUE) {
            // At this magnitude ULP is far larger than 1, so nanos cannot change the result.
            return units;
        }

        if (nanos == 0) {
            return value.negative() ? -(double)units: units;
        } else {
            // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
            // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
            long lo1 = units * NANOS_PER_UNIT;
            long lo = lo1 + nanos;
            long hi = Math.multiplyHigh(units, NANOS_PER_UNIT); // units >= 0: signed == unsigned
            if (Long.compareUnsigned(lo, lo1) < 0) {
                ++hi;
            }

            double v = scaleToDoubleV3(hi, lo);

            return value.negative() ? -v : v;
        }
    }

    public static double toDoubleV4(FPDecimal value) {
        long units = value.units();
        int nanos = value.nanos();

        // Handle Long.MIN_VALUE separately, because we will work with positive significand,
        // but -Long.MIN_VALUE does not fit into 64 bits
        if (units == Long.MIN_VALUE) {
            // At this magnitude ULP is far larger than 1, so nanos cannot change the result.
            return units;
        }

        if (nanos == 0) {
            return value.negative() ? -(double)units: units;
        } else {
            // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
            // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
            long lo1 = units * NANOS_PER_UNIT;
            long lo = lo1 + nanos;
            long hi = Math.multiplyHigh(units, NANOS_PER_UNIT); // units >= 0: signed == unsigned
            if (Long.compareUnsigned(lo, lo1) < 0) {
                ++hi;
            }

            double v = scaleToDoubleV4(hi, lo);

            return value.negative() ? -v : v;
        }
    }

    public static double toDoubleV5(FPDecimal value) {
        long units = value.units();
        int nanos = value.nanos();

        // Handle Long.MIN_VALUE separately, because we will work with positive significand,
        // but -Long.MIN_VALUE does not fit into 64 bits
        if (units == Long.MIN_VALUE) {
            // At this magnitude ULP is far larger than 1, so nanos cannot change the result.
            return units;
        }

        if (nanos == 0) {
            return value.negative() ? -(double)units: units;
        } else {
            // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
            // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
            long lo1 = units * NANOS_PER_UNIT;
            long lo = lo1 + nanos;
            long hi = Math.multiplyHigh(units, NANOS_PER_UNIT); // units >= 0: signed == unsigned
            if (Long.compareUnsigned(lo, lo1) < 0) {
                ++hi;
            }

            double v = scaleToDoubleV5(hi, lo);

            return value.negative() ? -v : v;
        }
    }

    public static double toDoubleV6(FPDecimal value) {
        long units = value.units();
        int nanos = value.nanos();

        // Handle Long.MIN_VALUE separately, because we will work with positive significand,
        // but -Long.MIN_VALUE does not fit into 64 bits
        if (units == Long.MIN_VALUE) {
            // At this magnitude ULP is far larger than 1, so nanos cannot change the result.
            return units;
        }

        if (nanos == 0) {
            return value.negative() ? -(double)units: units;
        } else {
            // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
            // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
            long lo1 = units * NANOS_PER_UNIT;
            long lo = lo1 + nanos;
            long hi = Math.multiplyHigh(units, NANOS_PER_UNIT); // units >= 0: signed == unsigned
            if (Long.compareUnsigned(lo, lo1) < 0) {
                ++hi;
            }

            double v = scaleToDoubleV6(hi, lo);

            return value.negative() ? -v : v;
        }
    }

    public static double scaleToDoubleV1(long hi, long lo) {
        if (hi == 0) {
            if (Long.compareUnsigned(lo, FAST_MAX_SIG_D) <= 0) {
                // Clinger: W and 10^9 are both exact in double -> one correctly rounded division
                return (double) lo / 1e9;
            } else {
                return DecimalToDouble.compute_float(lo, -9);   // W fits 64 bits: exact input
            }
        } else {
            // W has 65..93 bits
            return FPRounding.toDoubleNanosHi(hi, lo);
        }
    }

    public static double scaleToDoubleV3(long hi, long lo) {
        if (hi == 0) {
            if (Long.compareUnsigned(lo, FAST_MAX_SIG_D) <= 0) {
                // Clinger: W and 10^9 are both exact in double -> one correctly rounded division
                return (double) lo / 1e9;
            } else {
                return DecimalToDouble.compute_float(lo, -9);   // W fits 64 bits: exact input
            }
        } else {
            // W has 65..93 bits
            return FPRoundingDouble.toDoubleNanosHi(hi, lo);
        }
    }

    public static double scaleToDoubleV4(long hi, long lo) {
        if (hi == 0 && Long.compareUnsigned(lo, FAST_MAX_SIG_D) <= 0) {
            // Clinger: W and 10^9 are both exact in double -> one correctly rounded division
            return (double) lo / 1e9;
        } else {
            return FPRoundingDouble.toDoubleNanosFlat(hi, lo);
        }
    }

    public static double scaleToDoubleV5(long hi, long lo) {
        if ((hi | (lo >>> 53)) == 0) {
            // Clinger: W and 10^9 are both exact in double -> one correctly rounded division
            return (double) lo / 1e9;
        } else {
            return FPRoundingDouble.toDoubleNanosFlatBranchless(hi, lo);
        }
    }

    public static double scaleToDoubleV6(long hi, long lo) {
        if ((hi | (lo >>> 53)) == 0) {
            // Clinger: W and 10^9 are both exact in double -> one correctly rounded division
            return (double) lo / 1e9;
        } else {
            return FPRoundingDouble.toDoubleNanosFlatBranchless23(hi, lo);
        }
    }
}
