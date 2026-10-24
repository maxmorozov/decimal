package io.github.maxmorozov.decimal;

/**
 * Fixed-point decimal with 9 fractional digits:
 * value = (-1)^negative * (units + nanos / 10^9),  units >= 0,  0 <= nanos < 10^9.
 * <p>
 * Conversion to double/float is correctly rounded (round-half-even) and uses
 * neither BigDecimal nor strings: Clinger fast path -> Eisel-Lemire -> exact fallback.
 */
public record FPDecimal(boolean negative, long units, int nanos) {

    private static final int NANOS_PER_UNIT = 1_000_000_000;

    private static final long D5_9 = 1_953_125L;   // 5^9, 2^20 < D < 2^21

    public FPDecimal {
        if (units < 0) {
            throw new IllegalArgumentException("units must be non-negative (sign is separate): " + units);
        }
        if (nanos < 0 || nanos >= NANOS_PER_UNIT) {
            throw new IllegalArgumentException("nanos out of range [0; 999999999]: " + nanos);
        }
    }

    private static final long FAST_MAX_SIG_D = 1L << 53;   // integers up to 2^53 are exact in double
    private static final long FAST_MAX_SIG_F = 1L << 24;   // ... and up to 2^24 in float

    public double toDouble() {
        // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
        // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
        long lo1 = units * NANOS_PER_UNIT;
        long hi = Math.multiplyHigh(units, NANOS_PER_UNIT);   // units >= 0: signed == unsigned
        long lo = lo1 + nanos;
        if (Long.compareUnsigned(lo, lo1) < 0) {
            ++hi;
        }

        double v;
        if (hi == 0) {
            if (Long.compareUnsigned(lo, FAST_MAX_SIG_D) <= 0) {
                // Clinger: W and 10^9 are both exact in double -> one correctly rounded division
                v = (double) lo / 1e9;
            } else {
                v = DecimalToDouble.compute_float(lo, -9);   // W fits 64 bits: exact input
            }
        } else {
            // W has 65..93 bits: keep the top 64 bits (w), remember the dropped ones (sticky).
            // W = w * 2^s + r,  0 <= r < 2^s  =>  value lies in [w; w+1] * 2^s * 10^-9.
            v = toDoubleNanosHi(hi, lo);
        }
        return negative ? -v : v;
    }

    public float toFloat() {
        // W = units * 10^9 + nanos as an unsigned 128-bit integer (hi:lo); value = W * 10^-9.
        // W < 2^63 * 10^9 + 10^9 < 2^93, so it never overflows 128 bits.
        long lo1 = units * NANOS_PER_UNIT;
        long hi = Math.multiplyHigh(units, NANOS_PER_UNIT);   // units >= 0: signed == unsigned
        long lo = lo1 + nanos;
        if (Long.compareUnsigned(lo, lo1) < 0) {
            ++hi;
        }

        float v;
        if (hi == 0) {
            if (Long.compareUnsigned(lo, FAST_MAX_SIG_F) <= 0) {
                v = (float) lo / 1e9f;                         // 10^9 = 2^9 * 5^9 is exact in float
            } else {
                v = DecimalToFloat.compute_float(lo, -9);
            }
        } else {
            v = toFloatNanosHi(hi, lo);
        }
        return negative ? -v : v;
    }

    /**
     * (hi:lo, unsigned, non-zero) * 10^-9 -> double, with correct rounding.
     */
    public static double toDoubleNanos(long hi, long lo) {
        return roundNanos(hi, lo, 53);
    }

    public static float toFloatNanos(long hi, long lo) {
        // The result of roundNanos(.., 24) is m * 2^e, where m <= 2^24; therefore, the cast to float is exact.
        return (float) roundNanos(hi, lo, 24);
    }

    private static double roundNanos(long hi, long lo, int precision) {
        int bits = hi != 0 ? 128 - Long.numberOfLeadingZeros(hi)
                : 64 - Long.numberOfLeadingZeros(lo);
        int sh = bits - 76;                       // >0: shift to the right, <0: to the left
        long h, l;
        boolean sticky;
        if (sh > 0) {
            sticky = (lo & ((1L << sh) - 1)) != 0;
            l = (lo >>> sh) | (hi << (64 - sh));
            h = hi >>> sh;
        } else if (sh == 0) {
            h = hi;
            l = lo;
            sticky = false;
        } else {
            int k = -sh;                          // 1..22 for lo > 2^53
            h = (hi << k) | (lo >>> (64 - k));
            l = lo << k;
            sticky = false;
        }
        // h < 2^12, so we compute (h:l) / D in two 32-bit steps.
        long cur = (h << 32) | (l >>> 32);
        long q1 = cur / D5_9;
        long r = cur - q1 * D5_9;
        cur = (r << 32) | (l & 0xFFFFFFFFL);
        long q0 = cur / D5_9;
        r = cur - q0 * D5_9;
        long q = (q1 << 32) | q0;                 // 55 or 56 bits
        sticky |= r != 0;

        int len = 64 - Long.numberOfLeadingZeros(q);
        int drop = len - precision;               // 2..3 for double, 31..32 for float
        long m = q >>> drop;
        long rem = q & ((1L << drop) - 1);
        long half = 1L << (drop - 1);
        if (rem > half || (rem == half && (sticky || (m & 1) != 0))) m++;

        // value = (q + frac) * 2^(sh-9) = m * 2^(drop + sh - 9)
        return Math.scalb((double) m, drop + sh - 9);
    }

    /**
     * hi != 0. (hi:lo) * 10^-9 -> double, round-half-even.
     */
    public static double toDoubleNanosHi(long hi, long lo) {
        int sh = (128 - Long.numberOfLeadingZeros(hi)) - 76;   // -11..17
        long h, l;
        boolean sticky;
        if (sh > 0) {
            sticky = (lo & ((1L << sh) - 1)) != 0;
            l = (lo >>> sh) | (hi << (64 - sh));
            h = hi >>> sh;
        } else {
            int k = -sh;                                       // 0..11
            h = (hi << k) | ((lo >>> 1) >>> (63 - k));         // correct even when k == 0
            l = lo << k;
            sticky = false;
        }

        long cur = (h << 32) | (l >>> 32);
        long q1 = cur / D5_9;
        long r = cur - q1 * D5_9;
        cur = (r << 32) | (l & 0xFFFFFFFFL);
        long q0 = cur / D5_9;
        r = cur - q0 * D5_9;
        long q = (q1 << 32) | q0;                              // 55 or 56 bits
        sticky |= r != 0;

        int drop = 2 + (int) (q >>> 55);                       // 2 or 3
        long m = q >>> drop;
        long rem = q & ((1L << drop) - 1);
        long half = 1L << (drop - 1);
        if (rem > half || (rem == half && (sticky || (m & 1) != 0))) {
            m++;
        }

        // value = m * 2^p, m in [2^52, 2^53]
        int p = drop + sh - 9;
        return Double.longBitsToDouble(((long) (p + 1074) << 52) + m);
    }

    /**
     * The same for float: m in [2^23, 2^24], drop = 31 or 32.
     */
    public static float toFloatNanosHi(long hi, long lo) {
        int sh = (128 - Long.numberOfLeadingZeros(hi)) - 76;   // -11..17
        long h, l;
        boolean sticky;
        if (sh > 0) {
            sticky = (lo & ((1L << sh) - 1)) != 0;
            l = (lo >>> sh) | (hi << (64 - sh));
            h = hi >>> sh;
        } else {
            int k = -sh;                                       // 0..11
            h = (hi << k) | ((lo >>> 1) >>> (63 - k));         // correct even when k == 0
            l = lo << k;
            sticky = false;
        }

        long cur = (h << 32) | (l >>> 32);
        long q1 = cur / D5_9;
        long r = cur - q1 * D5_9;
        cur = (r << 32) | (l & 0xFFFFFFFFL);
        long q0 = cur / D5_9;
        r = cur - q0 * D5_9;
        long q = (q1 << 32) | q0;                              // 55 or 56 bits
        sticky |= r != 0;

        int drop = 31 + (int) (q >>> 55);
        long m = q >>> drop;
        long rem = q & ((1L << drop) - 1);
        long half = 1L << (drop - 1);
        if (rem > half || (rem == half && (sticky || (m & 1) != 0))) {
            m++;
        }

        int p = drop + sh - 9;
        return Float.intBitsToFloat((int) (((long) (p + 149) << 23) + m));
    }
}
