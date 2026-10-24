package io.github.maxmorozov.bigmath;

public class BigMath {
    // ------------------------------------------- exact sig * 10^exp -> double

    /**
     * Exact reference conversion of sig * 10^exp to double (half-even), sig in [1; 10^16).
     * Slow but obviously correct: kept as an oracle for tests.
     */
    public static double toDoubleSlow(long sig, int exp) {
        long[] r = slowRound(sig, exp, 53, -1022);
        return Math.scalb((double) r[0], (int) r[1]);   // m <= 2^53: exact
    }

    /**
     * Same as {@link #toDoubleSlow} but rounds directly to float.
     */
    public static float toFloatSlow(long sig, int exp) {
        long[] r = slowRound(sig, exp, 24, -126);
        return Math.scalb((float) r[0], (int) r[1]);    // m <= 2^24: exact
    }

    /**
     * Exact reference: (hi:lo as unsigned 128-bit) * 10^exp -> double.
     */
    public static double toDoubleSlow(long hi, long lo, int exp) {
        long[] r = slowRound(limbs128(hi, lo), exp, 53, -1022);
        return Math.scalb((double) r[0], (int) r[1]);
    }

    /**
     * Exact reference: (hi:lo as unsigned 128-bit) * 10^exp -> float.
     */
    public static float toFloatSlow(long hi, long lo, int exp) {
        long[] r = slowRound(limbs128(hi, lo), exp, 24, -126);
        return Math.scalb((float) r[0], (int) r[1]);
    }

    private static int[] limbs128(long hi, long lo) {
        int[] a = new int[LIMBS];
        a[0] = (int) lo;
        a[1] = (int) (lo >>> 32);
        a[2] = (int) hi;
        a[3] = (int) (hi >>> 32);
        return a;
    }

    /**
     * Exact big-integer rounding of sig * 10^exp to a binary format with the given
     * precision (53 / 24 bits) and minimum normal exponent (-1022 / -126).
     * Returns {m, e} such that the result is m * 2^e (m may be 0).
     * <p>
     * Uses 10^exp = 5^exp * 2^exp, so value = num/den * 2^exp, where
     * exp >= 0: num = sig * 5^exp, den = 1
     * exp <  0: num = sig,         den = 5^(-exp)
     * The integer quotient (55..56 bits) plus a sticky bit from the remainder
     * is then rounded to `precision` bits (or fewer for subnormals).
     */
    private static long[] slowRound(long sig, int exp, int precision, int minExp) {
        int[] num = new int[LIMBS];
        num[0] = (int) sig;
        num[1] = (int) (sig >>> 32);
        return slowRound(num, exp, precision, minExp);
    }

    /**
     * Same, for an arbitrary (up to ~1000-bit) numerator given as limbs.
     */
    private static long[] slowRound(int[] num, int exp, int precision, int minExp) {
        int[] den = new int[LIMBS];
        den[0] = 1;
        if (exp >= 0) mulPow5(num, exp);
        else mulPow5(den, -exp);

        // Scale so that 2^54 <= num/den < 2^56  (quotient has 55 or 56 bits)
        int s = 55 - (bitLength(num) - bitLength(den));
        if (s >= 0) shl(num, s);
        else shl(den, -s);
        // value = (num/den) * 2^(exp - s)

        // Binary long division: q = floor(num / den), num = remainder
        shl(den, 56);
        long q = 0;
        for (int i = 55; i >= 0; i--) {
            shr1(den);
            if (cmp(num, den) >= 0) {
                sub(num, den);
                q |= 1L << i;
            }
        }
        boolean sticky = !isZero(num);

        int len = 64 - Long.numberOfLeadingZeros(q);   // 55 or 56
        int e = (len - 1) + exp - s;                    // value in [2^e, 2^(e+1))

        // Number of mantissa bits we can keep: `precision` for normals, fewer for subnormals
        int keep = e >= minExp ? precision : e - minExp + precision;
        if (keep < 0) return new long[]{0, 0};         // below half of the smallest subnormal

        int drop = len - keep;
        long m = q >>> drop;
        long rem = q & ((1L << drop) - 1);
        long half = 1L << (drop - 1);
        if (rem > half || (rem == half && (sticky || (m & 1) != 0))) m++;

        return new long[]{m, Math.max(e, minExp) - (precision - 1)};
    }

    // Minimal fixed-size unsigned big integer: little-endian 32-bit limbs.
    // 64 limbs = 2048 bits, enough for 5^398 << 56 and for the table generation
    // (2^(2z+128) with z <= 790).
    private static final int LIMBS = 64;

    private static final long[] POW5_SMALL = {
            1L, 5L, 25L, 125L, 625L, 3125L, 15625L, 78125L, 390625L,
            1953125L, 9765625L, 48828125L, 244140625L
    };

    /**
     * a *= m, m < 2^32
     */
    private static void mul(int[] a, long m) {
        long carry = 0;
        for (int i = 0; i < LIMBS; i++) {
            long p = (a[i] & 0xFFFFFFFFL) * m + carry;
            a[i] = (int) p;
            carry = p >>> 32;
        }
    }

    /**
     * a *= 5^k (5^13 is the largest power of 5 below 2^32)
     */
    private static void mulPow5(int[] a, int k) {
        while (k >= 13) {
            mul(a, 1_220_703_125L);   // 5^13
            k -= 13;
        }
        mul(a, POW5_SMALL[k]);
    }

    private static int bitLength(int[] a) {
        for (int i = LIMBS - 1; i >= 0; i--) {
            if (a[i] != 0) return 32 * i + 32 - Integer.numberOfLeadingZeros(a[i]);
        }
        return 0;
    }

    /**
     * a <<= s, s >= 0
     */
    private static void shl(int[] a, int s) {
        int ls = s >>> 5, bit = s & 31;
        for (int i = LIMBS - 1; i >= 0; i--) {
            int src = i - ls;
            int lo = src >= 0 ? a[src] : 0;
            int below = src - 1 >= 0 ? a[src - 1] : 0;
            a[i] = bit == 0 ? lo : (lo << bit) | (below >>> (32 - bit));
        }
    }

    /**
     * a >>= 1
     */
    private static void shr1(int[] a) {
        for (int i = 0; i < LIMBS; i++) {
            a[i] = (a[i] >>> 1) | (i + 1 < LIMBS ? a[i + 1] << 31 : 0);
        }
    }

    private static int cmp(int[] a, int[] b) {
        for (int i = LIMBS - 1; i >= 0; i--) {
            if (a[i] != b[i]) return Integer.compareUnsigned(a[i], b[i]);
        }
        return 0;
    }

    /**
     * a -= b, requires a >= b
     */
    private static void sub(int[] a, int[] b) {
        long borrow = 0;
        for (int i = 0; i < LIMBS; i++) {
            long d = (a[i] & 0xFFFFFFFFL) - (b[i] & 0xFFFFFFFFL) - borrow;
            a[i] = (int) d;
            borrow = d < 0 ? 1 : 0;
        }
    }

    private static boolean isZero(int[] a) {
        for (int x : a) if (x != 0) return false;
        return true;
    }
}
