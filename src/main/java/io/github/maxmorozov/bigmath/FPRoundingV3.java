package io.github.maxmorozov.bigmath;

import io.github.maxmorozov.decimal.FPDecimal;

/**
 * Implements long division by 1e9 with correct rounding to make double or float.
 * <p>
 * Supports 128-bit input for {@code double} and {@code float} and 64 bit input for {@code float}.
 * </p>
 */
public class FPRoundingV3 {
    // 10^9 = 2^9 * 5^9  =>  W * 10^-9 = (W / 5^9) * 2^-9
    private static final long D5_9 = 1_953_125L;          // 5^9;  2^20 < D5_9 < 2^21 (log2 ~= 20.898)
    private static final int EXP_SHIFT = 9;               // multiplier 2^-9

    private static final int NORM_BITS_D = 76;            // |W'| = 76 bits  =>  q = W'/D has 55 or 56 bits
    private static final int Q_MIN_BITS_D = 55;

    private static final int MANTISSA_EXPLICIT_BITS_D = 52;
    private static final int EXPONENT_BIAS_D = 1023;
    private static final int PRECISION_D = MANTISSA_EXPLICIT_BITS_D + 1;
    private static final int EXPONENT_OFFSET_D = EXPONENT_BIAS_D + MANTISSA_EXPLICIT_BITS_D - 1;
    private static final int MANTISSA_DROP_D = Q_MIN_BITS_D - PRECISION_D;

    private static final int NORM_BITS_F = 63;            // |W'| = 63 bits  =>  q = W'/D has 42 or 43 bits
    // bitLength(floor(2^62 / D)) = 42; q can be 42 or 43 bits long
    private static final int Q_MIN_BITS_F = 64 - Long.numberOfLeadingZeros((1L << (NORM_BITS_F - 1)) / D5_9); // 42

    private static final int MANTISSA_EXPLICIT_BITS_F = 23;
    private static final int EXPONENT_BIAS_F = 127;
    private static final int PRECISION_F = MANTISSA_EXPLICIT_BITS_F + 1;
    private static final int EXPONENT_OFFSET_F = EXPONENT_BIAS_F + MANTISSA_EXPLICIT_BITS_F - 1;
    private static final int MANTISSA_DROP_F = Q_MIN_BITS_F - PRECISION_F;


    /**
     * (hi:lo, unsigned, non-zero) * 10^-9 -> double, with correct rounding.
     * <p>
     * Generic, but slower than {@link FPRounding#toDoubleNanosHi(long, long)}.
     * </p>
     */
    public static double toDoubleNanos(long hi, long lo) {
        return roundNanos(hi, lo, 53);
    }

    /**
     * (hi:lo, unsigned, non-zero) * 10^-9 -> float, with correct rounding.
     * <p>
     * Generic, but slower than {@link FPRounding#toFloatNanosHi(long, long)}
     * and {@link FPRounding#toFloatNanosLo(long)}.
     * </p>
     */
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
     * Converts decimal to double.
     * <p>
     * (hi:lo) * 10^-9 -> double, hi != 0 (W from 2^64 to 2^93), round-half-even.
     * </p>
     */
    public static double toDoubleNanosHi(long hi, long lo) {
        // W has bits = 65..93; sh = bits - 76 lies in the range [-11; 17]
        int sh = (128 - Long.numberOfLeadingZeros(hi)) - NORM_BITS_D;
        long h, l;
        boolean sticky;
        if (sh > 0) {                                          // shift right, 1..17: lose low-order bits lo
            sticky = (lo & ((1L << sh) - 1)) != 0;
            l = (lo >>> sh) | (hi << (64 - sh));               // 64 - sh in [47; 63]
            h = hi >>> sh;
        } else {                                               // left shift by k = 0..11, no loss
            int k = -sh;
            h = (hi << k) | ((lo >>> 1) >>> (63 - k));         // = lo >>> (64 - k), but it's also correct when k == 0
            l = lo << k;
            sticky = false;
        }

        // q = floor(W' / D), r = W' mod D
        // W' = ABC, where A, B, C - are 32-bit numbers
        // 1. AB/D -> q1 and r
        // 2. rC/D -> q0 and r
        // 3. q = g1:g0 (55 or 56 bits) and sticky
        long cur = (h << 32) | (l >>> 32);                     // < 2^44
        long q1 = cur / D5_9;                                  // < 2^24
        long r = cur - q1 * D5_9;                              // < D < 2^21
        cur = (r << 32) | (l & 0xFFFFFFFFL);                   // < D * 2^32 < 2^53
        long q0 = cur / D5_9;                                  // < 2^32, since r < D
        r = cur - q0 * D5_9;
        long q = (q1 << 32) | q0;                              // [2^54; 2^56): 55 or 56 bits
        sticky |= r != 0;

        // 53 bits of mantissa: drop = len(q) - 53 = 2 or 3 (q >>> 55 == 1 <=> len == 56)
        int drop = MANTISSA_DROP_D + (int) (q >>> Q_MIN_BITS_D);
        long m = q >>> drop;
        long rem = q & ((1L << drop) - 1);
        long half = 1L << (drop - 1);
        // half-even rounding
        if (rem > half || (rem == half && (sticky || (m & 1) != 0))) {
            m++;
        }

        // value = m * 2^p, m in [2^52, 2^53]
        int p = drop + sh - EXP_SHIFT;
        // m has 53 bits, we need to leave 52 explicit bits; m' = m - 2^52
        // E = p + 52 + 1023 = p + 1075; 1023 - exponent offset for double
        // instead of calculating m', we can add 53 bit of m to the exponent
        // double value bits = (E << 52) | m' = ((E-1) << 52) + m
        // 53 bit of m (a carry) results in an exponent of +1 and a zero mantissa, which is the correct result
        return Double.longBitsToDouble(((long) (p + EXPONENT_OFFSET_D) << MANTISSA_EXPLICIT_BITS_D) + m);
    }

    /**
     * Converts decimal to float.
     * <p>
     * (hi:lo) * 10^-9 -> float, hi != 0 (W in [2^64; 2^93)).
     * </p>
     */
    public static float toFloatNanosHi(long hi, long lo) {
        int sh = (128 - Long.numberOfLeadingZeros(hi)) - NORM_BITS_F;     // 2..30, always > 0
        long w = (lo >>> sh) | (hi << (64 - sh));                         // 63 bits, the most significant ones are not lost
        boolean sticky = (lo & ((1L << sh) - 1)) != 0;
        return roundFloat(w, sticky, sh);
    }

    /**
     * Converts decimal to float.
     * <p>
     * lo (unsigned, lo != 0) * 10^-9 -> float, for hi == 0.
     * </p>
     */
    public static float toFloatNanosLo(long lo) {
        int sh = (64 - Long.numberOfLeadingZeros(lo)) - NORM_BITS_F;      // -62..1
        if (sh > 0) {                                                     // lo >= 2^63: shift right by 1 bit
            // sticky = (lo & ((1L << sh) - 1)) != 0
            // w = lo >>> sh
            // positive sh can only be 1, so we can simplify the expressions
            boolean sticky = (lo & 1) != 0;
            return roundFloat(lo >>> 1, sticky, sh);
        }
        return roundFloat(lo << -sh, false, sh);                          // without losses
    }

    /**
     * value = (w + ε) * 2^shift * 10^-9, where w ∈ [2^62; 2^63) and ε ∈ [0; 1)
     * is determined by the "sticky" flag (indicating that non-zero lower-order bits were discarded).
     * The result is a normalized float, using the "round-half-even" rounding mode.
     */
    private static float roundFloat(long w, boolean sticky, int shift) {
        long q = w / D5_9;                                    // 42 or 43 bits
        sticky |= w != q * D5_9;                              // remainder of division != 0

        int drop = MANTISSA_DROP_F + (int) (q >>> Q_MIN_BITS_F); // 18 or 19: q >>> 42 == 1 <=> q is 43-bit

        // round-half-even with sticky: add = half - 1 + (m_odd | sticky)
        long half = 1L << (drop - 1);
        long add = half - 1 + (((q >>> drop) | (sticky ? 1 : 0)) & 1);
        long m = (q + add) >>> drop;

        int p = drop + shift - EXP_SHIFT;                      // value ~= m * 2^p
        return Float.intBitsToFloat((int) (((long) (p + EXPONENT_OFFSET_F) << MANTISSA_EXPLICIT_BITS_F) + m));
    }

    public static void main(String[] args) {
        FPDecimal decimal1 = new FPDecimal(false, 9007552L, 144953729);
        FPDecimal decimal2 = new FPDecimal(false, 868298182114L, 104073920);
        FPDecimal decimal3 = new FPDecimal(false, 24605452552L, 999999999);

        float v = 0;
        for (int i = 0; i < 50_000; ++i) {
            v = v + decimal1.toFloat();
            v = v + decimal2.toFloat();
            v = v + decimal3.toFloat();
        }

    }
}
