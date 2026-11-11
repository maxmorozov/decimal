package io.github.maxmorozov.fpdecimal;

/**
 * Fixed-point decimal with 9 fractional digits:
 * value = (-1)^negative * (units + nanos / 10^9),  units >= 0,  0 <= nanos < 10^9.
 * <p>
 * Conversion to double/float is correctly rounded (round-half-even) and uses
 * neither BigDecimal nor strings: Clinger fast path -> Eisel-Lemire -> exact fallback.
 */
public record FPDecimal(boolean negative, long units, int nanos) {
    private static final int NANOS_PER_UNIT = 1_000_000_000;

    public FPDecimal {
        if (units < 0) {
            throw new IllegalArgumentException("units must be non-negative (sign is separate): " + units);
        }
        if (nanos < 0 || nanos >= NANOS_PER_UNIT) {
            throw new IllegalArgumentException("nanos out of range [0; 999999999]: " + nanos);
        }
    }

}
