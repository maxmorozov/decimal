package io.github.maxmorozov.decimal;

import io.github.maxmorozov.bigmath.BigMath;

/**
 * Fixed-point decimal with 9 fractional digits:
 * value = (-1)^negative * (units + nanos / 10^9),  units >= 0,  0 <= nanos < 10^9.
 * <p>
 * Conversion to double/float is correctly rounded (round-half-even) and uses
 * neither BigDecimal nor strings: Clinger fast path -> Eisel-Lemire -> exact fallback.
 */
public record FPDecimal(boolean negative, long units, int nanos) {

    public static final int NANOS_PER_UNIT = 1_000_000_000;

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
            int s = 64 - Long.numberOfLeadingZeros(hi);
            long w = (hi << (64 - s)) | (lo >>> s);
            boolean sticky = (lo & ((1L << s) - 1)) != 0;
            double a = DecimalToDouble.compute_float(w, -9);
            if (!sticky) {
                v = Math.scalb(a, s);                          // exact: no overflow/underflow possible
            } else if (w != -1L && a == DecimalToDouble.compute_float(w + 1, -9)) {
                v = Math.scalb(a, s);                          // both ends round alike -> so does everything between
            } else {
                //System.out.printf("%019d.%09d%n", units, nanos);
                v = BigMath.toDoubleSlow(hi, lo, -9);        // rare (~2^-11): exact fallback
            }
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
            int s = 64 - Long.numberOfLeadingZeros(hi);
            long w = (hi << (64 - s)) | (lo >>> s);
            boolean sticky = (lo & ((1L << s) - 1)) != 0;
            float a = DecimalToFloat.compute_float(w, -9);
            if (!sticky) {
                v = Math.scalb(a, s);
            } else if (w != -1L && a == DecimalToFloat.compute_float(w + 1, -9)) {
                v = Math.scalb(a, s);
            } else {
                v = BigMath.toFloatSlow(hi, lo, -9);
            }
        }
        return negative ? -v : v;
    }
}
