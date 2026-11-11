package io.github.maxmorozov.fpdecimal;

import io.github.maxmorozov.bigmath.FPRoundingFloat;
import io.github.maxmorozov.bigmath.FPRoundingV3;
import io.github.maxmorozov.decimal.Decimal;

import static io.github.maxmorozov.bigmath.FPRounding.toFloatNanosHi;
import static io.github.maxmorozov.bigmath.FPRounding.toFloatNanosLo;

public class FPDecimalConverterFloat {
    private static final int NANOS_PER_UNIT = 1_000_000_000;

    private static final long FAST_MAX_SIG_D = 1L << 53;   // integers up to 2^53 are exact in double
    private static final long FAST_MAX_SIG_F = 1L << 24;   // ... and up to 2^24 in float
    
    private FPDecimalConverterFloat() {}

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
    public static float toFloatV1(FPDecimal value) {
        long units = value.units();
        int nanos = value.nanos();

        // Handle Long.MIN_VALUE separately, because we will work with positive significand,
        // but -Long.MIN_VALUE does not fit into 64 bits
        if (units == Long.MIN_VALUE) {
            // At this magnitude ULP is far larger than 1, so nanos cannot change the result.
            return units;
        }

        if (nanos == 0) {
            return value.negative() ? -(float)units: units;
        } else {
            // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
            // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
            long lo1 = units * NANOS_PER_UNIT;
            long lo = lo1 + nanos;
            long hi = Math.multiplyHigh(units, NANOS_PER_UNIT); // units >= 0: signed == unsigned
            if (Long.compareUnsigned(lo, lo1) < 0) {
                ++hi;
            }

            float v = scaleToFloatV1(hi, lo);

            return value.negative() ? -v : v;
        }
    }

    public static float toFloatV3(FPDecimal value) {
        long units = value.units();
        int nanos = value.nanos();

        // Handle Long.MIN_VALUE separately, because we will work with positive significand,
        // but -Long.MIN_VALUE does not fit into 64 bits
        if (units == Long.MIN_VALUE) {
            // At this magnitude ULP is far larger than 1, so nanos cannot change the result.
            return units;
        }

        if (nanos == 0) {
            return value.negative() ? -(float)units: units;
        } else {
            // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
            // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
            long lo1 = units * NANOS_PER_UNIT;
            long lo = lo1 + nanos;
            long hi = Math.multiplyHigh(units, NANOS_PER_UNIT); // units >= 0: signed == unsigned
            if (Long.compareUnsigned(lo, lo1) < 0) {
                ++hi;
            }

            float v = scaleToFloatV3(hi, lo);

            return value.negative() ? -v : v;
        }
    }

    public static float toFloatV4(FPDecimal value) {
        long units = value.units();
        int nanos = value.nanos();

        // Handle Long.MIN_VALUE separately, because we will work with positive significand,
        // but -Long.MIN_VALUE does not fit into 64 bits
        if (units == Long.MIN_VALUE) {
            // At this magnitude ULP is far larger than 1, so nanos cannot change the result.
            return units;
        }

        if (nanos == 0) {
            return value.negative() ? -(float)units: units;
        } else {
            // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
            // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
            long lo1 = units * NANOS_PER_UNIT;
            long lo = lo1 + nanos;
            long hi = Math.multiplyHigh(units, NANOS_PER_UNIT); // units >= 0: signed == unsigned
            if (Long.compareUnsigned(lo, lo1) < 0) {
                ++hi;
            }

            float v = scaleToFloatV4(hi, lo);

            return value.negative() ? -v : v;
        }
    }

    public static float toFloatV5(FPDecimal value) {
        long units = value.units();
        int nanos = value.nanos();

        // Handle Long.MIN_VALUE separately, because we will work with positive significand,
        // but -Long.MIN_VALUE does not fit into 64 bits
        if (units == Long.MIN_VALUE) {
            // At this magnitude ULP is far larger than 1, so nanos cannot change the result.
            return units;
        }

        if (nanos == 0) {
            return value.negative() ? -(float)units: units;
        } else {
            // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
            // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
            long lo1 = units * NANOS_PER_UNIT;
            long lo = lo1 + nanos;
            long hi = Math.multiplyHigh(units, NANOS_PER_UNIT); // units >= 0: signed == unsigned
            if (Long.compareUnsigned(lo, lo1) < 0) {
                ++hi;
            }

            float v = scaleToFloatV5(hi, lo);

            return value.negative() ? -v : v;
        }
    }

    public static float toFloatV6(FPDecimal value) {
        long units = value.units();
        int nanos = value.nanos();

        // Handle Long.MIN_VALUE separately, because we will work with positive significand,
        // but -Long.MIN_VALUE does not fit into 64 bits
        if (units == Long.MIN_VALUE) {
            // At this magnitude ULP is far larger than 1, so nanos cannot change the result.
            return units;
        }

        if (nanos == 0) {
            return value.negative() ? -(float)units: units;
        } else {
            // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
            // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
            long lo1 = units * NANOS_PER_UNIT;
            long lo = lo1 + nanos;
            long hi = Math.multiplyHigh(units, NANOS_PER_UNIT); // units >= 0: signed == unsigned
            if (Long.compareUnsigned(lo, lo1) < 0) {
                ++hi;
            }

            float v = scaleToFloatV6(hi, lo);

            return value.negative() ? -v : v;
        }
    }
    
    public static float scaleToFloatV1(long hi, long lo) {
        if (hi == 0) {
            if (Long.compareUnsigned(lo, FAST_MAX_SIG_F) <= 0) {
                return (float) lo / 1e9f;                         // 10^9 = 2^9 * 5^9 is exact in float
            } else {
                return toFloatNanosLo(lo);
            }
        } else {
            return toFloatNanosHi(hi, lo);
        }
    }

    public static float scaleToFloatV3(long hi, long lo) {
        if (hi == 0) {
            if (Long.compareUnsigned(lo, FAST_MAX_SIG_F) <= 0) {
                return (float) lo / 1e9f;                         // 10^9 = 2^9 * 5^9 is exact in float
            } else {
                return FPRoundingV3.toFloatNanosLo(lo);
            }
        } else {
            return FPRoundingV3.toFloatNanosHi(hi, lo);
        }
    }

    public static float scaleToFloatV4(long hi, long lo) {
        if (hi == 0 && Long.compareUnsigned(lo, FAST_MAX_SIG_F) <= 0) {
            return (float) lo / 1e9f;
        } else {
            return FPRoundingFloat.toFloatNanosFlat(hi, lo);
        }
    }
    
    public static float scaleToFloatV5(long hi, long lo) {
        if ((hi | (lo >>> 24)) == 0) {
            // float: W < 2^24  <=>  hi == 0 && lo < 2^24
            return (float) lo / 1e9f;
        } else {
            return FPRoundingFloat.toFloatNanosFlatMask(hi, lo);
        }
    }

    public static float scaleToFloatV6(long hi, long lo) {
        if ((hi | (lo >>> 24)) == 0) {
            // float: W < 2^24  <=>  hi == 0 && lo < 2^24
            return (float) lo / 1e9f;
        } else {
            return FPRoundingFloat.toFloatNanosFlatMask1(hi, lo);
        }
    }
}
