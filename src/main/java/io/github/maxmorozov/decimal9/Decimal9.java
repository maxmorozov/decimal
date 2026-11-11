package io.github.maxmorozov.decimal9;

/**
 *
 * @param units The whole units of the amount
 * @param nanos Number of nano (10^-9) units of the amount.
 * <pre>
 * The value must be between -999,999,999 and +999,999,999 inclusive.
 * If `units` is positive, `nanos` must be positive or zero.
 * If `units` is zero, `nanos` can be positive, zero, or negative.
 * If `units` is negative, `nanos` must be negative or zero.
 * For example -1.75 is represented as `units`=-1 and `nanos`=-750,000,000.
 *
 * </pre>
 */
public record Decimal9(long units, int nanos) {
    private static final int NANOS_PER_UNIT = 1_000_000_000;

    public Decimal9 {
        if ((units < 0 && nanos > 0) || (units > 0 && nanos < 0)) {
            throw new IllegalArgumentException(
                    "Decimal has mixed units/nanos signs: units=" + units + ", nanos=" + nanos);
        }
        if (nanos >= NANOS_PER_UNIT || nanos <= -NANOS_PER_UNIT) {
            throw new IllegalArgumentException("Decimal nanos value is out of range: " + nanos);
        }
    }
}
