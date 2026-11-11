package io.github.maxmorozov.decimal9;

import io.github.maxmorozov.schubfach.Schubfach;
import io.github.maxmorozov.schubfach.Schubfach.DoubleEncoder;

class DecimalEncoder implements DoubleEncoder {
    private static final int NANO_SCALE = 9;
    private static final int NANOS_PER_UNIT = 1_000_000_000;

    private long units;
    private int nanos;

    @Override
    public void encodeDouble(boolean negative, long significand, int exponent) {
        convert(negative, significand, exponent);
    }

    public Decimal9 result() {
        return new Decimal9(units, nanos);
    }

    /**
     * Converting decimal floating point value to a fixed point representation.
     *
     * @param negative true if the value is negative
     * @param significand significand value
     * @param exponent exponent value
     */
    private void convert(boolean negative, long significand, int exponent) {
        if (significand == 0L) {
            units = 0;
            nanos = 0;
            return;
        }

        long unitsAbs;
        int nanosAbs;

        if (exponent >= 0) {
            // value = s * 10^e, fractional part is missing
            checkExponent(negative, significand, exponent);
            unitsAbs = Math.multiplyExact(significand, Schubfach.MathUtils.pow10(exponent));
            nanosAbs = 0;
        } else {
            int scale = -exponent;

            // unitsAbs = floor(s / 10^scale)
            if (scale > Schubfach.MathUtils.MAX_POW10_EXPONENT) {
                // s < 2^63 < 10^19 <= 10^scale, so |value| < 1, all value digits will go to the fractional part
                unitsAbs = 0L;
                nanosAbs = roundFracToNanos(significand, scale);
            } else {
                long div = Schubfach.MathUtils.pow10(scale);
                unitsAbs = significand / div;
                long rem = significand % div;

                nanosAbs = roundRemToNanos(rem, scale);

                // if nanos is rounded to 1_000_000_000 -> transfer to units
                if (nanosAbs == NANOS_PER_UNIT) {
                    ++unitsAbs;
                    nanosAbs = 0;
                }
            }
        }

        if (!negative) {
            units = unitsAbs;
            nanos = nanosAbs;
            return;
        }

        // Canonical negative form:
        // -X.Y -> units = -X, nanos = -Y (if X != 0), otherwise units=0,nanos=-Y
        units = -unitsAbs;
        nanos = -nanosAbs;
    }

    private static void checkExponent(boolean negative, long significand, int exponent) {
        if (exponent > Schubfach.MathUtils.MAX_POW10_EXPONENT) {
            throw new ArithmeticException(
                    "Overflow converting a number to Decimal: " + (negative ? "-" : "") + significand + "E" + exponent);
        }
    }

    // for the case when scale >= POW10.length: round( significand * 1e9 / 10^scale )
    private static int roundFracToNanos(long significand, int scale) {
        // if scale - 9 >= POW10.length, result is too small -> 0 nanos
        int d = scale - NANO_SCALE;
        if (d > Schubfach.MathUtils.MAX_POW10_EXPONENT) {
            return 0;
        }

        return roundHalfUp(significand, d);
    }

    // round( rem * 1e9 / 10^scale ), where 0 <= rem < 10^scale
    private static int roundRemToNanos(long rem, int scale) {
        // scale >= 1 always (called only when exponent < 0)
        if (rem == 0) {
            return 0;
        }

        if (scale <= NANO_SCALE) {
            // Multiply: rem * 10^(9-scale); there is no overflow because rem < 10^scale <= 10^9.
            return (int) (rem * Schubfach.MathUtils.pow10(NANO_SCALE - scale));
        }

        // scale > 9: divide rem by 10^(scale-9), using half-up rounding.
        return roundHalfUp(rem, scale - NANO_SCALE);
    }

    /**
     * Rounding the significand to the specifies number of digits, using half-up method.
     *
     * @param significand value to round.
     * @param d number of digits to round to. Should be in range [0..POW10.length)
     * @return scaled value
     */
    private static int roundHalfUp(long significand, int d) {
        long div = Schubfach.MathUtils.pow10(d);
        long quotient = significand / div;
        long remainder = significand % div;

        // half-up
        if ((remainder << 1) >= div) {
            ++quotient;
        }

        return (int) quotient;
    }
}
