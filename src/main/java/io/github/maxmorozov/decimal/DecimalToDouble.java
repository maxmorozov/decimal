package io.github.maxmorozov.decimal;

import static io.github.maxmorozov.decimal.FastTable.*;

/**
 * Implements decimal to double conversion with correct rounding.
 * <p/>
 * Based on <a href="https://github.com/fastfloat/fast_float/blob/main/include/fast_float/decimal_to_binary.h">
 * fast_float::compute_float()</a>
 */
public class DecimalToDouble {

    // for binary64 (double)
    private static final int mantissa_explicit_bits = 52;
    private static final int minimum_exponent = -1023;
    private static final int min_exponent_round_to_even = -4;
    private static final int max_exponent_round_to_even = 23;
    private static final int infinite_power = 0x7FF;

    // The required precision is mantissa_explicit_bits + 3 because
    // 1. We need the implicit bit
    // 2. We need an extra bit for rounding purposes
    // 3. We might lose a bit due to the "upperbit" routine (result too small,
    // requiring a shift)
    private static final int bit_precision = mantissa_explicit_bits + 3;
    private static final long precision_mask = 0xFFFFFFFFFFFFFFFFL >>> bit_precision;

    // fast path
    private static final double[] double_powers_of_ten = {
            1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
            1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
    };
    // Largest integer n such that every integer in [0; n] is exactly representable as double (2^53).
    private static final long max_mantissa_fast_path = 2L << mantissa_explicit_bits;

    // Largest k such that 10^k is exactly representable as double (5^22 < 2^53 < 5^23).
    private static final int max_exp_fast_path = 22;

    /**
     * Correctly rounded  {@code +-w * 10^q} -> {@code double} (w in [0; 2^63), q any)
     *
     * @param negative indicates the sign of the number
     * @param sig      significand of the decimal number
     * @param exp      exponent of the decimal number
     * @return double value
     */
    public static double asDouble(boolean negative, long sig, int exp) {
        double v;
        // We start with a fast path
        // It was described in Clinger WD.
        // How to read floating point numbers accurately.
        // ACM SIGPLAN Notices. 1990
        if (sig <= max_mantissa_fast_path && exp >= -max_exp_fast_path && exp <= max_exp_fast_path) {
            // Fast path (Clinger): both operands are exactly representable as
            // double, so a single IEEE operation yields a correctly rounded result.
            v = exp >= 0 ? (double) sig * double_powers_of_ten[exp]
                    : (double) sig / double_powers_of_ten[-exp];
        } else {
            // General path: exact big-integer arithmetic, no strings.
            v = compute_float(sig, exp);
        }
        return negative ? -v : v;
    }

    /**
     * Eisel-Lemire: correctly rounded {@code w * 10^q} -> {@code double} (w in [0; 2^63), q any).
     * Port of fast_float::compute_float with the exact-tie (round-half-even) check.
     * <p>Reference:</p>
     * <lu>
     * <li>Lemire, "Number Parsing at a Gigabyte per Second";</li>
     * <li>Mushtak & Lemire, "Fast Number Parsing Without Fallback" (arXiv 2212.06644).</li>
     * </lu>
     *
     * @param w significand part of decimal value
     * @param q exponent of decimal value
     */
    public static double compute_float(long w, int q) {
        if (w == 0 || q < smallest_power_of_five) {
            return 0.0;
        }
        if (q > largest_power_of_five) {
            return Double.POSITIVE_INFINITY;
        }

        // At this point in time q is in [powers::smallest_power_of_five,
        // powers::largest_power_of_five].

        // We want the most significant bit of i to be 1. Shift if needed.
        int lz = Long.numberOfLeadingZeros(w);
        w <<= lz;                                   // MSB of w is now 1

        // This will compute or rather approximate w * 5^q and return a pair of 64-bit
        // words approximating the result, with the "high" part corresponding to the
        // most significant bits and the low part corresponding to the least significant
        // bits.
        // compute_product_approximation() in fast_float::compute_float
        int idx = 2 * (q - smallest_power_of_five);

        // 128-bit product w * 5^q (approximated by the top 64 + 64 table bits)
        // For small values of q, e.g., q in [0,27], the answer is always exact
        long hi = Math.unsignedMultiplyHigh(w, power_of_five_128[idx]);
        long lo = w * power_of_five_128[idx];

        if ((hi & precision_mask) == precision_mask) {        // 55-bit precision: 52 + implicit + round + 1 spare
            // could further guard with  (lower + w < lower)
            // regarding the second product, we only need hi2, but our expectation is
            // that the compiler will optimize this extra work away if needed.
            long hi2 = Math.unsignedMultiplyHigh(w, power_of_five_128[idx + 1]);
            lo += hi2;
            if (Long.compareUnsigned(hi2, lo) > 0) {
                hi++;
            }
        }

        // The computed product (hi, lo pair) is always sufficient.
        // Mathematical proof:
        // Noble Mushtak and Daniel Lemire, Fast Number Parsing Without Fallback (to appear)
        // See https://github.com/fastfloat/fast_float/blob/main/script/mushtak_lemire.py

        // The "compute_product_approximation" function can be slightly slower than a
        // branchless approach: value128 product = compute_product(q, w); but in
        // practice, we can win big with the compute_product_approximation if its
        // additional branch is easily predicted. Which is best is data specific.
        int upperbit = (int) (hi >>> 63);
        int shift = upperbit + 64 - bit_precision;
        long mantissa = hi >>> shift;               // 54 or 55 bits

        // inline power(q)
        //
        //  For q in (0,350), we have that
        //   f = (((152170 + 65536) * q ) >> 16);
        //  is equal to
        //    floor(p) + q
        //  where
        //    p = log(5^q)/log(2) = q * log(5)/log(2)
        //
        //  For negative values of q in (-400,0), we have that
        //   f = (((152170 + 65536) * q ) >> 16);
        //  is equal to
        //    -ceil(p) + q
        //  where
        //    p = log(5^-q)/log(2) = -q * log(5)/log(2)
        //
        int power2 = (int) (((152170L + 65536L) * q) >> 16) + 63 + upperbit - lz - minimum_exponent;

        if (power2 <= 0) {                          // subnormal (or zero)
            // Here have that power2 <= 0 so -power2 >= 0
            if (-power2 + 1 >= 64) {
                // if we have more than 64 bits below the minimum exponent, you
                // have a zero for sure.
                return 0.0;
            }
            // next line is safe because -power2 + 1 < 64
            mantissa >>>= -power2 + 1;
            // A subnormal result can also fall exactly between two floats. With at
            // most 19 digits this never happens for float and double
            mantissa += mantissa & 1;               // round up (ties cannot occur here)
            mantissa >>>= 1;
            // There is a weird scenario where we don't have a subnormal but just.
            // Suppose we start with 2.2250738585072013e-308, we end up
            // with 0x3fffffffffffff x 2^-1023-53 which is technically subnormal
            // whereas 0x40000000000000 x 2^-1023-53  is normal. Now, we need to round
            // up 0x3fffffffffffff x 2^-1023-53  and once we do, we are no longer
            // subnormal, but we can only know this after rounding.
            // So we only declare a subnormal if we are smaller than the threshold.
            power2 = mantissa < (1L << mantissa_explicit_bits) ? 0 : 1; // may have become normal after rounding
            return Double.longBitsToDouble(mantissa | ((long) power2 << mantissa_explicit_bits));
        }

        // usually, we round *up*, but if we fall right in between, and we have an
        // even basis, we need to round down
        // We are only concerned with the cases where 5^q fits in single 64-bit word.
        if (Long.compareUnsigned(lo, 1) <= 0 && q >= min_exponent_round_to_even && q <= max_exponent_round_to_even &&
                (mantissa & 3) == 1) {
            // we may fall between two floats!
            // To be in-between two floats we need that in doing
            //   mantissa = hi >> (upperbit + 64 - mantissa_explicit_bits - 3);
            // ... we dropped out only zeroes. But if this happened, then we can go back!!!
            if ((mantissa << shift) == hi) {
                mantissa &= ~1L;    // flip it so that we do not round up
            }
        }

        mantissa += mantissa & 1;                   // round up
        mantissa >>>= 1;
        // Both fix-ups below are rare.
        if (mantissa >= (2L << mantissa_explicit_bits)) {  // rounding carried into a new bit
            mantissa = 1L << mantissa_explicit_bits;
            power2++;                                      // undo previous addition
        }
        mantissa &= ~(1L << mantissa_explicit_bits);       // drop the implicit bit

        if (power2 >= infinite_power) {
            return Double.POSITIVE_INFINITY;
        }
        return Double.longBitsToDouble(mantissa | ((long) power2 << mantissa_explicit_bits));
    }

}
