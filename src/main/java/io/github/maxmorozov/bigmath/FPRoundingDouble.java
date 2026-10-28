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
     * Experimental: round half up on the main path (one add and one shift, no sticky needed), and fix the
     * exact tie in a rare branch where the sticky bit and the parity are examined lazily.
     */
    public static double toDoubleNanosHiLazy(long hi, long lo) {
        int sh = (128 - Long.numberOfLeadingZeros(hi)) - NORM_BITS_D;
        long h, l;
        boolean sticky;
        if (sh > 0) {
            sticky = (lo & ((1L << sh) - 1)) != 0;
            l = (lo >>> sh) | (hi << (64 - sh));
            h = hi >>> sh;
        } else {
            int k = -sh;
            h = (hi << k) | ((lo >>> 1) >>> (63 - k));
            l = lo << k;
            sticky = false;
        }
        long cur = (h << 32) | (l >>> 32);
        long q1 = cur / D5_9;
        long r = cur - q1 * D5_9;
        cur = (r << 32) | (l & 0xFFFFFFFFL);
        long q0 = cur / D5_9;
        long q = (q1 << 32) | q0;

        int drop = MANTISSA_DROP_D + (int) (q >>> Q_MIN_BITS_D);
        long half = 1L << (drop - 1);
        long m = (q + half) >>> drop;                          // round half up
        if ((q & ((half << 1) - 1)) == half                    // exact tie in q (rare)
                && !sticky && cur == q0 * D5_9                 // nothing was dropped or left in the remainder
                && (m & 1) != 0) {                             // rounded up to odd -> round to even instead
            m--;
        }
        int p = drop + sh - EXP_SHIFT;
        return Double.longBitsToDouble(((long) (p + EXPONENT_OFFSET_D) << MANTISSA_EXPLICIT_BITS_D) + m);
    }

    /**
     * Experimental: normalization without a branch on the sign of sh and without max():
     * W is always shifted left by 11 bits first (no loss, W << 11 has at most 104 bits), and then always shifted
     * right by t = sh + 11 = 0..28 bits. Rounding is the same as in toDoubleNanosHi.
     */
    public static double toDoubleNanosHiFlat(long hi, long lo) {
        int sh = (128 - Long.numberOfLeadingZeros(hi)) - NORM_BITS_D;   // -11..17
        long h0 = (hi << 11) | (lo >>> 53);
        long l0 = lo << 11;
        int t = sh + 11;                                                 // 0..28
        boolean sticky = (l0 & ((1L << t) - 1)) != 0;                    // the 11 low bits of l0 are zeros
        long l = (l0 >>> t) | ((h0 << 1) << (63 - t));                   // = l0 when t == 0
        long h = h0 >>> t;
        return roundDouble(h, l, sticky, sh);
    }

    /**
     * Experimental: the flat normalization + round-half-up with a lazy fix of the exact tie.
     */
    public static double toDoubleNanosHiFlatLazy(long hi, long lo) {
        int sh = (128 - Long.numberOfLeadingZeros(hi)) - NORM_BITS_D;
        long h0 = (hi << 11) | (lo >>> 53);
        long l0 = lo << 11;
        int t = sh + 11;
        boolean sticky = (l0 & ((1L << t) - 1)) != 0;
        long l = (l0 >>> t) | ((h0 << 1) << (63 - t));
        long h = h0 >>> t;

        long cur = (h << 32) | (l >>> 32);
        long q1 = cur / D5_9;
        long r = cur - q1 * D5_9;
        cur = (r << 32) | (l & 0xFFFFFFFFL);
        long q0 = cur / D5_9;
        long q = (q1 << 32) | q0;

        int drop = MANTISSA_DROP_D + (int) (q >>> Q_MIN_BITS_D);
        long half = 1L << (drop - 1);
        long m = (q + half) >>> drop;                          // round half up
        if ((q & ((half << 1) - 1)) == half                    // exact tie in q (rare)
                && !sticky && cur == q0 * D5_9
                && (m & 1) != 0) {
            m--;
        }
        int p = drop + sh - EXP_SHIFT;
        return Double.longBitsToDouble(((long) (p + EXPONENT_OFFSET_D) << MANTISSA_EXPLICIT_BITS_D) + m);
    }

    /**
     * Experimental: one branch-free path for every W in [2^53; 2^93) (hi == 0 is allowed), so that the caller only
     * has to separate the Clinger case (W <= 2^53). W is always shifted left by 22 bits first (no loss, at most
     * 115 bits), then always right by t = sh + 22 = 0..39 bits, where sh = bits - 76 is in [-22; 17].
     */
    public static double toDoubleNanosFlat(long hi, long lo) {
        int nlzHi = Long.numberOfLeadingZeros(hi);
        int nlz = nlzHi + (-(nlzHi >>> 6) & Long.numberOfLeadingZeros(lo));    // 128-bit W; no ternary: nlzHi >>> 6 == 1 only for hi == 0
        int sh = (128 - nlz) - NORM_BITS_D;                                    // -22..17 for W > 2^53
        long h0 = (hi << 22) | (lo >>> 42);
        long l0 = lo << 22;
        int t = sh + 22;                                                       // 0..39
        boolean sticky = (l0 & ((1L << t) - 1)) != 0;                          // the 22 low bits of l0 are zeros
        long l = (l0 >>> t) | ((h0 << 1) << (63 - t));
        long h = h0 >>> t;
        return roundDouble(h, l, sticky, sh);
    }

    /**
     * The same as toDoubleNanosFlat, but without anything the JIT can turn into a data-dependent branch:
     * the leading zeros of the 128-bit W and the sticky bit are computed arithmetically.
     * In the "integers" profile (nanos == 0) the sticky bit changes from value to value, and C2 compiled
     * both "(l0 & mask) != 0" and "r != 0" into branches.
     */
    public static double toDoubleNanosFlatBranchless(long hi, long lo) {
        int nlzHi = Long.numberOfLeadingZeros(hi);
        int nlz = nlzHi + (-(nlzHi >>> 6) & Long.numberOfLeadingZeros(lo));
        int sh = (128 - nlz) - NORM_BITS_D;
        long h0 = (hi << 22) | (lo >>> 42);
        long l0 = lo << 22;
        int t = sh + 22;
        long lost = l0 & ((1L << t) - 1);
        long l = (l0 >>> t) | ((h0 << 1) << (63 - t));
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
        long x = lost | r;                                     // non-zero <=> sticky
        long sticky = (x | -x) >>> 63;                         // 0 or 1, no branch

        // 53 bits of mantissa: drop = len(q) - 53 = 2 or 3 (q >>> 55 == 1 <=> len == 56)
        int drop = MANTISSA_DROP_D + (int) (q >>> Q_MIN_BITS_D);

        // round-half-even with sticky: add = half - 1 + (m_odd | sticky), m = (q + add) >>> drop
        long half = 1L << (drop - 1);
        long add = half - 1 + (((q >>> drop) | sticky) & 1);
        long m = (q + add) >>> drop;

        // value = m * 2^p, m in [2^52; 2^53]
        int p = drop + sh - EXP_SHIFT;
        // E = p + 52 + 1023, bits = (E << 52) | (m - 2^52) = ((E - 1) << 52) + m;
        // a carry (m == 2^53) increments the exponent and zeroes the mantissa, which is the correct result
        return Double.longBitsToDouble(((long) (p + EXPONENT_OFFSET_D) << MANTISSA_EXPLICIT_BITS_D) + m);
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

    /**
     * Clinger path for W = lo < 2^53: lo * 10^-9 with one correctly rounded division.
     * <p>
     * A plain (double) lo compiles to vcvtsi2sd, which on JDK 21 has a false dependency on the previous value of
     * the destination register: in a loop it makes a chain of ~17 cycles (3 ns instead of 0.8 ns).
     * Here the integer is converted without that instruction. Both 32-bit halves are exact doubles
     * (2^52 + x has the mantissa field = x, and subtracting 2^52 gives x back), and
     * a * 2^32 + b is an integer below 2^53, so the sum is exact too.
     * </p>
     */
    public static double clingerToDouble(long lo) {
        double a = Double.longBitsToDouble(0x4330000000000000L | (lo >>> 32)) - 0x1.0p52;
        double b = Double.longBitsToDouble(0x4330000000000000L | (lo & 0xFFFFFFFFL)) - 0x1.0p52;
        return (a * 0x1.0p32 + b) / 1e9;
    }
}
