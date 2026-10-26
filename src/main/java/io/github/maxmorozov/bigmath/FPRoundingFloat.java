package io.github.maxmorozov.bigmath;

/**
 * Float counterpart of FPRoundingDouble.toDoubleNanosFlat: one branch-free path (cmov-able selects only)
 * for every W = (hi:lo) > 2^24, with or without hi == 0. The caller only has to separate the Clinger case.
 * <p>
 * The constants are copied from FPRounding so that the class can be compiled on its own.
 */
public final class FPRoundingFloat {
    private static final long D5_9 = 1_953_125L;          // 5^9;  2^20 < D5_9 < 2^21
    private static final int EXP_SHIFT = 9;               // multiplier 2^-9

    private static final int NORM_BITS_F = 63;            // w in [2^62; 2^63)  =>  q = w/D has 42 or 43 bits
    private static final int Q_MIN_BITS_F = 64 - Long.numberOfLeadingZeros((1L << (NORM_BITS_F - 1)) / D5_9); // 42

    private static final int MANTISSA_EXPLICIT_BITS_F = 23;
    private static final int EXPONENT_BIAS_F = 127;
    private static final int PRECISION_F = MANTISSA_EXPLICIT_BITS_F + 1;
    private static final int EXPONENT_OFFSET_F = EXPONENT_BIAS_F + MANTISSA_EXPLICIT_BITS_F - 1;
    private static final int MANTISSA_DROP_F = Q_MIN_BITS_F - PRECISION_F;

    private FPRoundingFloat() {
    }

    /**
     * (hi:lo) * 10^-9 -> float for any W in (2^24; 2^93), round-half-even.
     * <p>
     * W is aligned to the top of 128 bits (z = leading zeros), then w = the top 63 bits of the result,
     * and everything below them is the sticky bit. Both ways of the alignment (hi != 0 and hi == 0)
     * are computed, and one of them is selected, so there is no data-dependent branch.
     * </p>
     */
    public static float toFloatNanosFlat(long hi, long lo) {
        int z1 = Long.numberOfLeadingZeros(hi);                       // 0..63 if hi != 0, 64 if hi == 0
        int z0 = Long.numberOfLeadingZeros(lo);
        boolean hiZero = hi == 0;

        // hi != 0: (H:L) = W << z1.  For hi == 0 the shift counts are garbage, but this result is not selected
        long hHi = (hi << z1) | ((lo >>> 1) >>> (63 - z1));
        long lHi = lo << z1;
        // hi == 0: (H:L) = lo << (64 + z0)
        long hLo = lo << z0;

        long h = hiZero ? hLo : hHi;
        long l = hiZero ? 0 : lHi;
        int z = hiZero ? 64 + z0 : z1;                                // leading zeros of the 128-bit W

        long w = h >>> 1;                                             // 63 bits, the top one is set
        boolean sticky = (l | (h & 1)) != 0;                          // everything that was shifted out
        return roundFloat(w, sticky, 65 - z);                         // W ~ w * 2^(65 - z)
    }

    /**
     * The same as toFloatNanosFlat, but the choice between hi != 0 and hi == 0 is made with masks, not with
     * ternaries, so it cannot be compiled into a branch. If hi == 0, lo is moved to the high word
     * (W' = lo * 2^64, which has 64 more bits of shift to account for).
     */
    public static float toFloatNanosFlatMask(long hi, long lo) {
        long nz = (hi | -hi) >> 63;                                   // -1 if hi != 0, 0 if hi == 0
        long hiN = hi | (lo & ~nz);                                   // never zero
        long loN = lo & nz;
        int zz = Long.numberOfLeadingZeros(hiN);                      // 0..63
        long h = (hiN << zz) | ((loN >>> 1) >>> (63 - zz));
        long l = loN << zz;
        int z = zz + (int) (~nz & 64);                                // leading zeros of the 128-bit W

        long w = h >>> 1;
        boolean sticky = (l | (h & 1)) != 0;
        return roundFloat(w, sticky, 65 - z);
    }

    private static float roundFloat(long w, boolean sticky, int shift) {
        long q = w / D5_9;                                    // 42 or 43 bits
        sticky |= w != q * D5_9;                              // remainder of division != 0

        int drop = MANTISSA_DROP_F + (int) (q >>> Q_MIN_BITS_F); // 18 or 19

        // round-half-even with sticky: add = half - 1 + (m_odd | sticky)
        long half = 1L << (drop - 1);
        long add = half - 1 + (((q >>> drop) | (sticky ? 1 : 0)) & 1);
        long m = (q + add) >>> drop;

        int p = drop + shift - EXP_SHIFT;                     // value ~= m * 2^p
        return Float.intBitsToFloat((int) (((long) (p + EXPONENT_OFFSET_F) << MANTISSA_EXPLICIT_BITS_F) + m));
    }
}
