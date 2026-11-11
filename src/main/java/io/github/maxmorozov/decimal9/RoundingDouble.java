package io.github.maxmorozov.decimal9;

/**
 * Double rounding of scaled 128-bit integer value: (hi:lo) * 10^-9 -> double with round-half-even.
 *
 * <p>
 *
 * <ul>
 *   <li>10^9 = 2^9 * 5^9, so the only real division is by D = 5^9 (a constant, compiled to a multiplication);
 *   <li>rounding is done by adding a correction and shifting: no branches on rem/half;
 *   <li>"sticky" is a plain boolean, the JIT chooses between a branch and cmov by the profile.
 * </ul>
 */
public final class RoundingDouble {
    private static final int NANO_SCALE = 9;

    // 10^9 = 2^9 * 5^9  =>  W * 10^-9 = (W / 5^9) * 2^-9
    private static final long D5_9 = 1_953_125L; // 5^9;  2^20 < D5_9 < 2^21 (log2 ~= 20.898)
    private static final int EXP_SHIFT = NANO_SCALE; // multiplier 2^-9

    private static final int NORM_BITS = 76; // W' in [2^75; 2^76)  =>  q = W'/D has 55 or 56 bits
    private static final int Q_MIN_BITS = 55;

    private static final int MANTISSA_EXPLICIT_BITS = 52;
    private static final int EXPONENT_BIAS = 1023;
    private static final int PRECISION = MANTISSA_EXPLICIT_BITS + 1;
    private static final int EXPONENT_OFFSET = EXPONENT_BIAS + MANTISSA_EXPLICIT_BITS - 1;
    private static final int MANTISSA_DROP = Q_MIN_BITS - PRECISION; // drop = 2 or 3

    private static final long FAST_MAX_SIG = 1L << PRECISION; // integers up to 2^53 are exact in double

    // Minimal shift to normalize W to [2^75; 2^76) is -22, so L >= 22 is enough for the left part.
    // L = 23 keeps t = sh + L >= 1, so h0 << (64 - t) never becomes a shift by 64.
    // On general path W has bits = 54..93, we need to meet 93 + L <= 128, so L should be in [23; 35].
    private static final int L = NORM_BITS - (Long.SIZE - Long.numberOfLeadingZeros(FAST_MAX_SIG)) + 1; // 23

    private RoundingDouble() {}

    /**
     * Scales the 128-bit unsigned integer (hi:lo) by 10^-9 and returns the result as a double. Implements a branchless
     * path for W in [2^53; 2^93) (hi == 0 is allowed). The Clinger case (W < 2^53) is calculated using double division.
     *
     * <p>The input is assumed to be in the range [0; 2^93), so the result is always finite and non-negative.
     *
     * @param hi the high 64 bits of the unsigned integer
     * @param lo the low 64 bits of the unsigned integer
     * @return the scaled double value
     */
    public static double scaleToDouble(long hi, long lo) {
        if ((hi | (lo >>> PRECISION)) == 0) {
            // We start with a fast path
            // It was described in Clinger WD.
            // How to read floating point numbers accurately.
            // ACM SIGPLAN Notices. 1990
            // W and 10^9 are both exact in double -> one correctly rounded division
            return lo / 1e9;
        } else {
            // W has 54..93 bits
            return toDoubleNanosBranchless(hi, lo);
        }
    }

    /**
     * One branch-free path for every W in [2^53; 2^93) (hi == 0 is allowed), so that the caller only has to separate
     * the Clinger case (W < 2^53).
     *
     * <p>W is always shifted left by 23 bits first (no loss, at most 116 bits), then always right by t = sh + 23 =
     * 1..40 bits, where sh = bits - 76 is in [-22; 17].
     *
     * <p>Without anything the JIT can turn into a data-dependent branch: the leading zeros of the 128-bit W and the
     * sticky bit are computed arithmetically.
     */
    private static double toDoubleNanosBranchless(long hi, long lo) {
        // Normalizing the 128-bit W = (hi:lo) to W' = (h:l) in [2^75; 2^76) by shifting left or right
        // by sh = bits - 76.
        // W has bits = 54..93, so sh = bits - 76 lies in the range [-22; 17]
        int nlzHi = Long.numberOfLeadingZeros(hi);
        int nlz = nlzHi + (-(nlzHi >>> 6) & Long.numberOfLeadingZeros(lo));
        int sh = (128 - nlz) - NORM_BITS;
        // Implement shift by sh in [-22; 17] as a left shift by L = 23 followed by a right shift
        // by t = sh + L in [1; 40].
        long h0 = (hi << L) | (lo >>> (64 - L));
        long l0 = lo << L;
        int t = sh + L;
        // The low-order bits of l0, which were dropped by the right shift, are used to compute the "sticky" bit.
        long lost = l0 & ((1L << t) - 1);
        // Java masks the shift distance to 6 bits, so h0 << 64 would be h0 << 0; t >= 1 keeps 64 - t in [24; 63].
        long l = (l0 >>> t) | (h0 << (64 - t));
        long h = h0 >>> t;

        // q = floor(W' / D), r = W' mod D
        // W' = ABC, where A, B, C are 32-bit digits
        // 1. AB / D -> q1 and r
        // 2. rC / D -> q0 and r
        // 3. q = q1:q0 (55 or 56 bits)
        long cur = (h << 32) | (l >>> 32);
        long q1 = cur / D5_9;
        long r = cur - q1 * D5_9;
        cur = (r << 32) | (l & 0xFFFFFFFFL);
        long q0 = cur / D5_9;
        r = cur - q0 * D5_9;
        long q = (q1 << 32) | q0;
        long x = lost | r; // non-zero <=> sticky
        // branchless calculation sticky = x != 0 ? 1: 0
        long sticky = (x | -x) >>> 63; // 0 or 1, no branch

        // 53 bits of mantissa: drop = len(q) - 53 = 2 or 3 (q >>> 55 == 1 <=> len == 56)
        int drop = MANTISSA_DROP + (int) (q >>> Q_MIN_BITS);

        // round-half-even with sticky: add = half - 1 + (m_odd | sticky), m = (q + add) >>> drop
        long half = 1L << (drop - 1);
        long add = half - 1 + (((q >>> drop) | sticky) & 1);
        long m = (q + add) >>> drop;

        // value = m * 2^p, m in [2^52; 2^53]
        int p = drop + sh - EXP_SHIFT;
        // m has 53 bits, we need to leave 52 explicit bits; m' = m - 2^52
        // E = p + 52 + 1023 = p + 1075; 1023 - exponent offset for double
        // instead of calculating m', we can add 53 bit of m to the exponent
        // double value bits = (E << 52) | m' = ((E - 1) << 52) + m
        // a carry (m == 2^53) increments the exponent and zeroes the mantissa, which is the correct result
        return Double.longBitsToDouble(((long) (p + EXPONENT_OFFSET) << MANTISSA_EXPLICIT_BITS) + m);
    }
}
