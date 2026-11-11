package io.github.maxmorozov.decimal9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

import io.github.maxmorozov.schubfach.Schubfach;
import org.junit.jupiter.api.Test;

/**
 * Tests DecimalConverter.fromDouble() against a reference: the shortest decimal of the double ({@link Double#toString},
 * shortest since JDK 19) rounded half-up to 9 fractional digits.
 */
class DecimalConverterEncodeTest {

    private static final long SEED = 0xDEC1L;
    private static final int ITERATIONS = 200_000;
    private static final double TWO_POW_63 = 0x1p63;
    private static final int MAX_EXACT_DIGITS = 15;

    private static Decimal9 reference(double v) {
        BigDecimal exact = new BigDecimal(Double.toString(v)).setScale(9, RoundingMode.HALF_UP);
        BigDecimal units = new BigDecimal(exact.toBigInteger());
        return new Decimal9(units.longValueExact(), exact.subtract(units).movePointRight(9).intValueExact());
    }

    private static void check(double v) {
        assertEquals(reference(v), Decimal9Converter.fromDouble(v), () -> "v=" + v);
    }

    /** Log-uniform magnitudes from far below a nano up to just under 2^63, both signs. */
    @Test
    void randomMagnitudesMatchReference() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < ITERATIONS; i++) {
            double v = Math.scalb(1.0 + rnd.nextDouble(), rnd.nextInt(-45, 63));
            check(rnd.nextBoolean() ? v : -v);
        }
    }

    /** Values near the 9th fractional digit, where half-up rounding and carries into units happen. */
    @Test
    void valuesAroundNanoBoundaryMatchReference() {
        Random rnd = new Random(SEED + 1);
        for (int i = 0; i < ITERATIONS; i++) {
            long units = rnd.nextLong(0, 10_000_000L);
            long tenthsOfNano = rnd.nextLong(0, 10_000_000_000L);
            double v = Double.parseDouble(units + "." + String.format("%010d", tenthsOfNano));
            check(rnd.nextBoolean() ? v : -v);
        }
    }

    @Test
    void largestEncodableDoublesMatchReference() {
        double v = Math.nextDown(TWO_POW_63);
        for (int i = 0; i < 1_000; i++, v = Math.nextDown(v)) {
            check(v);
            check(-v);
        }
    }

    @Test
    void doublesFromTwoPow63AreRejected() {
        // 2^63 renders as 9.223372036854776E18, which exceeds Long.MAX_VALUE.
        double v = TWO_POW_63;
        for (int i = 0; i < 1_000; i++, v = Math.nextUp(v)) {
            double value = v;
            assertThrows(IllegalArgumentException.class, () -> Decimal9Converter.fromDouble(value));
            assertThrows(IllegalArgumentException.class, () -> Decimal9Converter.fromDouble(-value));
        }
    }

    /** A Decimal from the UI with at most 15 significant digits survives toDouble followed by encode. */
    @Test
    void decimalsWithUpTo15DigitsRoundTripThroughDouble() {
        Random rnd = new Random(SEED + 2);
        for (int i = 0; i < ITERATIONS; i++) {
            int fractionDigits = rnd.nextInt(0, 10);
            int digits = rnd.nextInt(Math.max(1, fractionDigits), MAX_EXACT_DIGITS + 1);
            long significand = rnd.nextLong(0, Schubfach.MathUtils.pow10(digits));
            long pow = Schubfach.MathUtils.pow10(fractionDigits);
            long units = significand / pow;
            int nanos = (int) ((significand % pow) * Schubfach.MathUtils.pow10(9 - fractionDigits));
            boolean negative = rnd.nextBoolean();

            Decimal9 d = new Decimal9(negative ? -units : units, negative ? -nanos : nanos);

            assertEquals(d, Decimal9Converter.fromDouble(Decimal9Converter.toDouble(d)), d::toString);
        }
    }

    /** Beyond 15 significant digits the double in between can lose the last digit. */
    @Test
    void sixteenDigitDecimalMayNotRoundTrip() {
        Decimal9 d = new Decimal9(9_987_867L, 844_568_136);

        Decimal9 back = Decimal9Converter.fromDouble(Decimal9Converter.toDouble(d));

        assertNotEquals(d, back);
        assertEquals(844_568_135, back.nanos());
    }

    @Test
    void negativeValueBelowHalfNanoBecomesZero() {
        assertEquals(new Decimal9(0, 0), Decimal9Converter.fromDouble(-4e-10));
    }
}
