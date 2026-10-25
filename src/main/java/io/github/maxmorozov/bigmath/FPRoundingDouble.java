package io.github.maxmorozov.bigmath;

/**
 * Double counterpart of the float "v3" code: (hi:lo) * 10^-9 -> double with round-half-even.
 * <p>
 * Same ideas as for float:
 * <ul>
 *   <li>10^9 = 2^9 * 5^9, so the only real division is by D = 5^9 (a constant, compiled to a multiplication);</li>
 *   <li>rounding is done by adding a correction and shifting: no branches on rem/half;</li>
 *   <li>"sticky" is a plain boolean, the JIT chooses between a branch and cmov by the profile.</li>
 * </ul>
 * The constants are copied from FPRounding so that the class can be compiled on its own;
 * when merging, move the methods into FPRounding and drop the constants.
 */
public final class FPRoundingDouble {
    private static final long D5_9 = 1_953_125L;          // 5^9;  2^20 < D5_9 < 2^21 (log2 ~= 20.898)
    private static final int EXP_SHIFT = 9;               // multiplier 2^-9

    private static final int NORM_BITS_D = 76;            // W' in [2^75; 2^76)  =>  q = W'/D has 55 or 56 bits
    private static final int Q_MIN_BITS_D = 55;

    private static final int MANTISSA_EXPLICIT_BITS_D = 52;
    private static final int EXPONENT_BIAS_D = 1023;
    private static final int PRECISION_D = MANTISSA_EXPLICIT_BITS_D + 1;
    private static final int EXPONENT_OFFSET_D = EXPONENT_BIAS_D + MANTISSA_EXPLICIT_BITS_D - 1;
    private static final int MANTISSA_DROP_D = Q_MIN_BITS_D - PRECISION_D;   // drop = 2 or 3

    private FPRoundingDouble() {
    }

    /**
     * (hi:lo) * 10^-9 -> double, hi != 0 (W in [2^64; 2^93)), round-half-even.
     */
    public static double toDoubleNanosHi(long hi, long lo) {
        // W has bits = 65..93; sh = bits - 76 lies in the range [-11; 17]
        int sh = (128 - Long.numberOfLeadingZeros(hi)) - NORM_BITS_D;
        long h, l;
        boolean sticky;
        if (sh > 0) {                                          // shift right, 1..17: lose low-order bits of lo
            sticky = (lo & ((1L << sh) - 1)) != 0;
            l = (lo >>> sh) | (hi << (64 - sh));               // 64 - sh in [47; 63]
            h = hi >>> sh;
        } else {                                               // left shift by k = 0..11, no loss
            int k = -sh;
            h = (hi << k) | ((lo >>> 1) >>> (63 - k));         // = lo >>> (64 - k), but also correct when k == 0
            l = lo << k;
            sticky = false;
        }
        return roundDouble(h, l, sticky, sh);
    }

    /**
     * Experimental: the same as toDoubleNanosHi, but both normalization shifts (right by s, left by k) are always
     * executed, one of them by 0 bits, so there is no data-dependent branch on the sign of sh.
     */
    public static double toDoubleNanosHiBranchless(long hi, long lo) {
        int sh = (128 - Long.numberOfLeadingZeros(hi)) - NORM_BITS_D;   // -11..17
        int s = Math.max(sh, 0);                                         // right shift, 0..17
        int k = Math.max(-sh, 0);                                        // left shift, 0..11
        boolean sticky = (lo & ((1L << s) - 1)) != 0;
        long l = (lo >>> s) | ((hi << 1) << (63 - s));
        long h = hi >>> s;
        h = (h << k) | ((l >>> 1) >>> (63 - k));
        l <<= k;
        return roundDouble(h, l, sticky, sh);
    }

    /**
     * lo (unsigned) * 10^-9 -> double for hi == 0 and lo > 2^53 (for lo <= 2^53 use (double) lo / 1e9),
     * round-half-even.
     * <p>
     * lo has 54..64 bits, so the normalization shift k = 76 - bits = 12..22 is always to the left:
     * nothing is lost and there are no branches.
     * </p>
     */
    public static double toDoubleNanosLo(long lo) {
        int k = Long.numberOfLeadingZeros(lo) + 12;            // 76 - bits, 12..22 for lo > 2^53
        long h = lo >>> (64 - k);                              // 12 bits
        long l = lo << k;
        return roundDouble(h, l, false, -k);
    }

    /**
     * value = ((h:l) + eps) * 2^shift * 10^-9, where (h:l) is in [2^75; 2^76), h < 2^12, and eps in [0; 1)
     * is given by "sticky" (non-zero low-order bits were dropped by the normalization).
     * The result is a normalized double, rounded half-even.
     */
    private static double roundDouble(long h, long l, boolean sticky, int shift) {
        // q = floor(W' / D), r = W' mod D;  W' = ABC, where A, B, C are 32-bit digits
        // 1. AB / D -> q1 and r;  2. rC / D -> q0 and r;  3. q = q1:q0 (55 or 56 bits)
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

        // round-half-even with sticky: add = half - 1 + (m_odd | sticky), m = (q + add) >>> drop
        long half = 1L << (drop - 1);
        long add = half - 1 + (((q >>> drop) | (sticky ? 1 : 0)) & 1);
        long m = (q + add) >>> drop;                           // 53 bits, or 2^53 after a carry

        // value = m * 2^p, m in [2^52; 2^53]
        int p = drop + shift - EXP_SHIFT;
        // E = p + 52 + 1023, bits = (E << 52) | (m - 2^52) = ((E - 1) << 52) + m;
        // a carry (m == 2^53) increments the exponent and zeroes the mantissa, which is the correct result
        return Double.longBitsToDouble(((long) (p + EXPONENT_OFFSET_D) << MANTISSA_EXPLICIT_BITS_D) + m);
    }
}
