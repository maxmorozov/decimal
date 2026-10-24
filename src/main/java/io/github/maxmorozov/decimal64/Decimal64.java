package io.github.maxmorozov.decimal64;

/**
 * IEEE 754-2008 decimal64, BID (Binary Integer Decimal) encoding.
 * <p>
 * 64-bit layout:
 * bit 63 - sign
 * Form 1 (significand < 2^53):
 * bits 62..53 - biased exponent (10 bits, bias = 398)
 * bits 52..0  - significand
 * Form 2 (bits 62..61 == 11, significand >= 2^53):
 * bits 60..51 - biased exponent
 * significand = 2^53 + bits 50..0
 * Bits 62..58 == 11110 - Infinity, 11111 - NaN.
 * <p>
 * Value = (-1)^sign * significand * 10^exponent,
 * significand <= 9 999 999 999 999 999, exponent in [-398; 369].
 */
public final class Decimal64 {

    private Decimal64() {
    }

    public static final int BIAS = 398;
    public static final int MIN_EXP = -398;
    public static final int MAX_EXP = 369;
    public static final long MAX_SIG = 9_999_999_999_999_999L; // 10^16 - 1

    /**
     * BID form 1 stores the significand in 53 bits, so it holds values below 2^53.
     */
    private static final long BID_FORM1_LIMIT = 1L << 53;

    /**
     * Largest n such that every integer in [0; n] is exactly representable as double (2^53).
     */
    private static final long FAST_PATH_MAX_SIG = 1L << 53;
    /**
     * Largest k such that 10^k is exactly representable as double (5^22 < 2^53 < 5^23).
     */
    private static final int FAST_PATH_MAX_EXP = 22;

    /**
     * Same for float: 2^24; 5^10 < 2^24 < 5^11.
     */
    private static final long FAST_PATH_MAX_SIG_F = 1L << 24;
    private static final int FAST_PATH_MAX_EXP_F = 10;

    private static final long SIGN_MASK = 1L << 63;
    private static final long INF_BITS = 0x7800000000000000L;
    public static final long NAN_BITS = 0x7C00000000000000L;

    private static final long[] POW10 = {
            1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L,
            100_000_000L, 1_000_000_000L, 10_000_000_000L, 100_000_000_000L,
            1_000_000_000_000L, 10_000_000_000_000L, 100_000_000_000_000L,
            1_000_000_000_000_000L, 10_000_000_000_000_000L,
            100_000_000_000_000_000L, 1_000_000_000_000_000_000L
    };

    // ------------------------------------------------------------------ encode

    /**
     * Encodes (-1)^negative * significand * 10^exponent as decimal64 BID.
     * If the value cannot be represented exactly, it is rounded half-even;
     * on overflow, Infinity is returned.
     *
     * @param significand non-negative significand (the sign is passed separately)
     */
    public static long encode(boolean negative, long significand, int exponent) {
        if (significand < 0) {
            throw new IllegalArgumentException("significand must be non-negative: " + significand);
        }
        long sign = negative ? SIGN_MASK : 0L;

        // 1. More than 16 digits: drop the excess digits with rounding
        if (significand > MAX_SIG) {
            int drop = digitCount(significand) - 16;
            significand = roundDiv(significand, drop);
            exponent += drop;
            if (significand > MAX_SIG) {      // rounded up to 10^16
                significand /= 10;
                exponent++;
            }
        }

        // 2. Bring the exponent into the valid range
        if (significand == 0) {
            exponent = Math.max(MIN_EXP, Math.min(MAX_EXP, exponent));
        } else if (exponent < MIN_EXP) {
            int shift = MIN_EXP - exponent;
            significand = shift > 18 ? 0 : roundDiv(significand, (int) shift);
            exponent = MIN_EXP;
        } else if (exponent > MAX_EXP) {
            int shift = exponent - MAX_EXP;
            // Try to compensate by appending zeros to the significand (clamping).
            // A d-digit significand can take at most (16 - d) extra zeros.
            if (shift > 16 - digitCount(significand)) {
                return sign | INF_BITS;       // overflow
            }
            significand *= POW10[(int) shift];
            exponent = MAX_EXP;
        }

        // 3. Packing
        long biased = exponent + BIAS;        // 0..767, fits into 10 bits
        long bits;
        if (significand < BID_FORM1_LIMIT) {
            bits = (biased << 53) | significand;
        } else {
            bits = (3L << 61) | (biased << 51) | (significand - BID_FORM1_LIMIT);
        }
        return sign | bits;
    }

    // ------------------------------------------------------------------ decode

    public static boolean isNegative(long bits) {
        return bits < 0;
    }

    public static boolean isInfinite(long bits) {
        return ((bits >>> 58) & 0x1F) == 0x1E;
    }

    public static boolean isNaN(long bits) {
        return ((bits >>> 58) & 0x1F) == 0x1F;
    }

    /**
     * Significand (0 for non-canonical values, as required by the standard).
     */
    public static long significand(long bits) {
        long sig;
        if (((bits >>> 61) & 3) == 3) {
            sig = (1L << 53) | (bits & ((1L << 51) - 1));
        } else {
            sig = bits & ((1L << 53) - 1);
        }
        return sig > MAX_SIG ? 0 : sig;
    }

    /**
     * Unbiased exponent.
     */
    public static int exponent(long bits) {
        int biased = ((bits >>> 61) & 3) == 3
                ? (int) ((bits >>> 51) & 0x3FF)
                : (int) ((bits >>> 53) & 0x3FF);
        return biased - BIAS;
    }

    /**
     * Converts decimal64 (BID) to double with correct rounding.
     */
    public static double toDouble(long bits) {
        if (isNaN(bits)) return Double.NaN;
        boolean neg = isNegative(bits);
        if (isInfinite(bits)) return neg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

        long sig = significand(bits);
        int exp = exponent(bits);

        double v;
        if (sig == 0) {
            v = 0.0;
        } else if (sig <= FAST_PATH_MAX_SIG && exp >= -FAST_PATH_MAX_EXP && exp <= FAST_PATH_MAX_EXP) {
            // Fast path (Clinger): both operands are exactly representable as
            // double, so a single IEEE operation yields a correctly rounded result.
            v = exp >= 0 ? (double) sig * DOUBLE_POW10[exp]
                    : (double) sig / DOUBLE_POW10[-exp];
        } else {
            // General path: Eisel-Lemire (always correctly rounded for 64-bit input)
            v = toDoubleEiselLemire(sig, exp);
        }
        return neg ? -v : v;
    }

    private static final double[] DOUBLE_POW10 = {
            1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
            1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
    };

    // ------------------------------------------------------------ toFloat

    private static final float[] FLOAT_POW10 = {
            1e0f, 1e1f, 1e2f, 1e3f, 1e4f, 1e5f, 1e6f, 1e7f, 1e8f, 1e9f, 1e10f
    };

    /**
     * Converts decimal64 (BID) directly to float with correct rounding
     * (no intermediate double, which could cause double rounding).
     */
    public static float toFloat(long bits) {
        if (isNaN(bits)) return Float.NaN;
        boolean neg = isNegative(bits);
        if (isInfinite(bits)) return neg ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;

        long sig = significand(bits);
        int exp = exponent(bits);

        float v;
        if (sig == 0) {
            v = 0.0f;
        } else if (sig <= FAST_PATH_MAX_SIG_F && exp >= -FAST_PATH_MAX_EXP_F && exp <= FAST_PATH_MAX_EXP_F) {
            // Clinger fast path: both operands exact in float, one correctly rounded IEEE operation
            v = exp >= 0 ? (float) sig * FLOAT_POW10[exp]
                    : (float) sig / FLOAT_POW10[-exp];
        } else {
            v = toFloatEiselLemire(sig, exp);
        }
        return neg ? -v : v;
    }

    // For sig < 10^16 and float:
    //   q < -61 -> value < 1e-45 < half of Float.MIN_VALUE (~7e-46 ... 1.4e-45/2) -> 0
    //   q >  38 -> value >= 1e39 > Float.MAX_VALUE                                -> Infinity
    private static final int FLOAT_Q_MIN = -61;
    private static final int FLOAT_Q_MAX = 38;

    /**
     * Eisel-Lemire for float: correctly rounded w * 10^q, w in [1; 10^16), q any.
     * Same algorithm and table as the double version, with float parameters:
     * 23 explicit mantissa bits, bit precision 23 + 3 = 26, exponent bias 127,
     * and the round-half-even window q in [-17; 10].
     */
    static float toFloatEiselLemire(long w, int q) {
        if (w == 0 || q < FLOAT_Q_MIN) return 0.0f;
        if (q > FLOAT_Q_MAX) return Float.POSITIVE_INFINITY;

        int lz = Long.numberOfLeadingZeros(w);
        w <<= lz;

        long[] t = Table.T;
        int idx = 2 * (q - Q_MIN);

        long hi = umulh(w, t[idx]);
        long lo = w * t[idx];
        final long precisionMask = -1L >>> 26;      // 38 low bits
        if ((hi & precisionMask) == precisionMask) {
            long hi2 = umulh(w, t[idx + 1]);
            lo += hi2;
            if (Long.compareUnsigned(hi2, lo) > 0) hi++;
        }

        int upperbit = (int) (hi >>> 63);
        int shift = upperbit + 38;                  // 64 - 23 - 3
        long mantissa = hi >>> shift;               // 25 or 26 bits

        int power2 = power(q) + upperbit - lz + 127;

        if (power2 <= 0) {                          // subnormal (or zero)
            if (-power2 + 1 >= 64) return 0.0f;
            mantissa >>>= -power2 + 1;
            mantissa += mantissa & 1;
            mantissa >>>= 1;
            power2 = mantissa < (1L << 23) ? 0 : 1;
            return Float.intBitsToFloat((int) (mantissa | ((long) power2 << 23)));
        }

        // Exact halfway case: round to even instead of up
        if (Long.compareUnsigned(lo, 1) <= 0 && q >= -17 && q <= 10 && (mantissa & 3) == 1) {
            if ((mantissa << shift) == hi) {
                mantissa &= ~1L;
            }
        }

        mantissa += mantissa & 1;
        mantissa >>>= 1;
        if (mantissa >= (2L << 23)) {
            mantissa = 1L << 23;
            power2++;
        }
        mantissa &= ~(1L << 23);

        if (power2 >= 0xFF) return Float.POSITIVE_INFINITY;
        return Float.intBitsToFloat((int) (mantissa | ((long) power2 << 23)));
    }

    // ------------------------------------------- exact sig * 10^exp -> double

    /**
     * Exact reference conversion of sig * 10^exp to double (half-even), sig in [1; 10^16).
     * Slow but obviously correct: kept as an oracle for tests.
     */
    static double toDoubleSlow(long sig, int exp) {
        long[] r = slowRound(sig, exp, 53, -1022);
        return Math.scalb((double) r[0], (int) r[1]);   // m <= 2^53: exact
    }

    /**
     * Same as {@link #toDoubleSlow} but rounds directly to float.
     */
    static float toFloatSlow(long sig, int exp) {
        long[] r = slowRound(sig, exp, 24, -126);
        return Math.scalb((float) r[0], (int) r[1]);    // m <= 2^24: exact
    }

    /**
     * Exact reference: (hi:lo as unsigned 128-bit) * 10^exp -> double.
     */
    static double toDoubleSlow(long hi, long lo, int exp) {
        long[] r = slowRound(limbs128(hi, lo), exp, 53, -1022);
        return Math.scalb((double) r[0], (int) r[1]);
    }

    /**
     * Exact reference: (hi:lo as unsigned 128-bit) * 10^exp -> float.
     */
    static float toFloatSlow(long hi, long lo, int exp) {
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
        if (exp >= 0) {
            mulPow5(num, exp);
        } else {
            mulPow5(den, -exp);
        }

        // Scale so that 2^54 <= num/den < 2^56  (quotient has 55 or 56 bits)
        int s = 55 - (bitLength(num) - bitLength(den));
        if (s >= 0) {
            shl(num, s);
        } else {
            shl(den, -s);
        }
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

    // --------------------------------------------------- Eisel-Lemire

    // Powers of five covered by the table. For sig < 10^16:
    //   q < Q_MIN  -> value < 1e-325 < 2^-1075       -> 0.0
    //   q > Q_MAX  -> value >= 1e309 > Double.MAX    -> Infinity
    private static final int Q_MIN = -340;
    private static final int Q_MAX = 308;

    /**
     * Lazily built table: for each q in [Q_MIN, Q_MAX] a pair (high, low) of 64-bit words.
     */
    private static final class Table {
        static final long[] T = build();
    }

    /**
     * ~ floor(q * log2(10)) + 63; exact for |q| <= ~1650.
     */
    private static int power(int q) {
        return (int) (((152170L + 65536L) * q) >> 16) + 63;
    }

    /**
     * Unsigned 64x64 -> high 64 bits of the 128-bit product.
     */
    private static long umulh(long a, long b) {
        return Math.multiplyHigh(a, b) + ((a >> 63) & b) + ((b >> 63) & a);
    }

    /**
     * Eisel-Lemire: correctly rounded w * 10^q -> double (w in [1; 2^63), q any).
     * Port of fast_float::compute_float with the exact-tie (round-half-even) check.
     * Reference: Lemire, "Number Parsing at a Gigabyte per Second";
     * Mushtak & Lemire, "Fast Number Parsing Without Fallback" (arXiv 2212.06644).
     */
    static double toDoubleEiselLemire(long w, int q) {
        if (w == 0 || q < Q_MIN) return 0.0;
        if (q > Q_MAX) return Double.POSITIVE_INFINITY;

        int lz = Long.numberOfLeadingZeros(w);
        w <<= lz;                                   // MSB of w is now 1

        long[] t = Table.T;
        int idx = 2 * (q - Q_MIN);

        // 128-bit product w * 5^q (approximated by the top 64 + 64 table bits)
        long hi = umulh(w, t[idx]);
        long lo = w * t[idx];
        if ((hi & 0x1FF) == 0x1FF) {                // 55-bit precision: 52 + implicit + round + 1 spare
            long hi2 = umulh(w, t[idx + 1]);
            lo += hi2;
            if (Long.compareUnsigned(hi2, lo) > 0) hi++;
        }

        int upperbit = (int) (hi >>> 63);
        int shift = upperbit + 9;                   // 64 - 52 - 3
        long mantissa = hi >>> shift;               // 54 or 55 bits

        // power(q) ~ floor(q * log2(10)) + 63; 1023 is the exponent bias
        int power2 = power(q) + upperbit - lz + 1023;

        if (power2 <= 0) {                          // subnormal (or zero)
            if (-power2 + 1 >= 64) return 0.0;
            mantissa >>>= -power2 + 1;
            mantissa += mantissa & 1;               // round up (ties cannot occur here)
            mantissa >>>= 1;
            power2 = mantissa < (1L << 52) ? 0 : 1; // may have become normal after rounding
            return Double.longBitsToDouble(mantissa | ((long) power2 << 52));
        }

        // Exact halfway case: round to even instead of up
        if (Long.compareUnsigned(lo, 1) <= 0 && q >= -4 && q <= 23 && (mantissa & 3) == 1) {
            if ((mantissa << shift) == hi) {
                mantissa &= ~1L;
            }
        }

        mantissa += mantissa & 1;                   // round up
        mantissa >>>= 1;
        if (mantissa >= (2L << 52)) {               // rounding carried into a new bit
            mantissa = 1L << 52;
            power2++;
        }
        mantissa &= ~(1L << 52);                    // drop the implicit bit

        if (power2 >= 0x7FF) return Double.POSITIVE_INFINITY;
        return Double.longBitsToDouble(mantissa | ((long) power2 << 52));
    }

    /**
     * Builds the 128-bit table exactly as fast_float's table_generation.py does:
     * q >= 0 : top 128 bits of 5^q (truncated, MSB at bit 127)
     * q <  0 : floor(2^b / 5^k) + 1, k = -q, z = bitlen(5^k),
     * b = z + 127 (k <= 27) or 2z + 128 (k > 27), truncated to 128 bits.
     */
    private static long[] build() {
        long[] t = new long[2 * (Q_MAX - Q_MIN + 1)];

        int[] p = new int[LIMBS];
        p[0] = 1;
        for (int q = 0; q <= Q_MAX; q++) {
            store(t, q, p);                         // p = 5^q, normalised inside store()
            mul(p, 5);
        }

        int[] p5 = new int[LIMBS];
        p5[0] = 1;
        for (int k = 1; k <= -Q_MIN; k++) {
            mul(p5, 5);                             // p5 = 5^k
            int z = bitLength(p5);                  // 5^k is not a power of two: ceil(log2) = bitLength
            int b = k <= 27 ? z + 127 : 2 * z + 128;

            int[] c = new int[LIMBS];
            c[b >>> 5] = 1 << (b & 31);             // 2^b
            divPow5(c, k);                          // floor(2^b / 5^k)
            for (int i = 0; i < LIMBS; i++) {       // + 1
                if (++c[i] != 0) break;
            }
            store(t, -k, c);
        }
        return t;
    }

    /**
     * Stores the top 128 bits of a (truncated; shifted up if a is shorter) into t for power q.
     */
    private static void store(long[] t, int q, int[] a) {
        int[] c = a.clone();
        int bl = bitLength(c);
        if (bl < 128) shl(c, 128 - bl);
        else if (bl > 128) shr(c, bl - 128);
        int idx = 2 * (q - Q_MIN);
        t[idx] = ((c[3] & 0xFFFFFFFFL) << 32) | (c[2] & 0xFFFFFFFFL);
        t[idx + 1] = ((c[1] & 0xFFFFFFFFL) << 32) | (c[0] & 0xFFFFFFFFL);
    }

    /**
     * a >>= s, s >= 0
     */
    private static void shr(int[] a, int s) {
        int ls = s >>> 5, bit = s & 31;
        for (int i = 0; i < LIMBS; i++) {
            int src = i + ls;
            int lo = src < LIMBS ? a[src] : 0;
            int hi = src + 1 < LIMBS ? a[src + 1] : 0;
            a[i] = bit == 0 ? lo : (lo >>> bit) | (hi << (32 - bit));
        }
    }

    /**
     * a /= d (floor), 0 < d < 2^31
     */
    private static void divSmall(int[] a, long d) {
        long rem = 0;
        for (int i = LIMBS - 1; i >= 0; i--) {
            long cur = (rem << 32) | (a[i] & 0xFFFFFFFFL);
            a[i] = (int) (cur / d);
            rem = cur % d;
        }
    }

    /**
     * a /= 5^k (floor); floor(floor(x/m)/n) == floor(x/(m*n)), so chunking is exact.
     */
    private static void divPow5(int[] a, int k) {
        while (k >= 13) {
            divSmall(a, 1_220_703_125L);            // 5^13
            k -= 13;
        }
        divSmall(a, POW5_SMALL[k]);
    }

    // ----------------------------------------------------------------- helpers

    /**
     * Number of decimal digits in v (v > 0), computed without loops.
     */
    private static int digitCount(long v) {
        // approximate floor(log10(v)) from the bit length, then correct by one
        int t = ((64 - Long.numberOfLeadingZeros(v)) * 1233) >>> 12;
        return t - (v < POW10[t] ? 1 : 0) + 1;
    }

    /**
     * v / 10^digits with half-even rounding. digits: 1..18.
     */
    private static long roundDiv(long v, int digits) {
        long d = POW10[digits];
        long q = v / d;
        long r = v % d;
        long half = d / 2;
        if (r > half || (r == half && (q & 1) == 1)) q++;
        return q;
    }

    // -------------------------------------------------------------------- demo

    public static void main(String[] args) {
        // 8100000000000000e-15 = 8.1
        long bits = encode(false, 8100000000000000L, -15);
        System.out.printf("bits   = 0x%016X%n", bits);
        System.out.println("sig    = " + significand(bits));
        System.out.println("exp    = " + exponent(bits));
        System.out.println("double = " + toDouble(bits));

        // value > 2^53 (second encoding form)
        long b2 = encode(true, 9_999_999_999_999_999L, -2);
        System.out.printf("bits2  = 0x%016X -> %s%n", b2, toDouble(b2));
    }
}
