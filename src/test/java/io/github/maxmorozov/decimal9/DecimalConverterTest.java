package io.github.maxmorozov.decimal9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.maxmorozov.schubfach.Schubfach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DecimalConverterTest {
    private DecimalEncoder converter;

    @BeforeEach
    void setUp() {
        this.converter = new DecimalEncoder();
    }

    @Test
    void shouldConvertSimplePositive() {
        Decimal9 m = Decimal9Converter.fromDouble(12.34);
        assertEquals(12L, m.units());
        assertEquals(340_000_000, m.nanos());
    }

    @Test
    void shouldConvertSimpleNegative() {
        Decimal9 m = Decimal9Converter.fromDouble(-12.34);

        assertEquals(-12L, m.units());
        assertEquals(-340_000_000, m.nanos());
    }

    @Test
    void shouldHandleZero() {
        Decimal9 m1 = Decimal9Converter.fromDouble(0.0);
        Decimal9 m2 = Decimal9Converter.fromDouble(-0.0);

        assertEquals(0L, m1.units());
        assertEquals(0, m1.nanos());

        assertEquals(0L, m2.units());
        assertEquals(0, m2.nanos());
    }

    @Test
    void shouldRoundHalfUpAtNinthDigit_DownCase() {
        // 9th digit is 9, next digit is 4 -> round down
        Decimal9 m = Decimal9Converter.fromDouble(1.999_999_999_4);
        assertEquals(1L, m.units());
        assertEquals(999_999_999, m.nanos());
    }

    @Test
    void shouldRoundHalfUpAtNinthDigit_UpCase() {
        // 9th digit is 9, next digit is 5 -> round up with carry
        Decimal9 m = Decimal9Converter.fromDouble(1.999_999_999_5);
        assertEquals(2L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldRoundTinyValueToZero() {
        Decimal9 m = Decimal9Converter.fromDouble(0.000_000_000_4);
        assertEquals(0L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldRoundTinyValueUpToOneNano() {
        Decimal9 m = Decimal9Converter.fromDouble(0.000_000_000_5);
        assertEquals(0L, m.units());
        assertEquals(1, m.nanos());
    }

    @Test
    void shouldRejectNaN() {
        assertThrows(IllegalArgumentException.class, () -> Decimal9Converter.fromDouble(Double.NaN));
    }

    @Test
    void shouldRejectPositiveInfinity() {
        assertThrows(IllegalArgumentException.class, () -> Decimal9Converter.fromDouble(Double.POSITIVE_INFINITY));
    }

    @Test
    void shouldRejectNegativeInfinity() {
        assertThrows(IllegalArgumentException.class, () -> Decimal9Converter.fromDouble(Double.NEGATIVE_INFINITY));
    }

    @Test
    void shouldThrowOnOverflowForHugeValue() {
        assertThrows(IllegalArgumentException.class, () -> Decimal9Converter.fromDouble(Double.MAX_VALUE));
    }

    @Test
    void shouldThrowOnOverflowForBigValue() {
        assertThrows(IllegalArgumentException.class, () -> Decimal9Converter.fromDouble(1e19));
    }

    @Test
    void shouldReportValueWhenUnitsOverflowBelowMaxExponent() {
        // 9.3e18 renders as 93E17: the exponent fits, the multiplication by 10^17 does not.
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> Decimal9Converter.fromDouble(-9.3e18));
        assertTrue(e.getMessage().contains("-9.3E18"), e.getMessage());
    }

    // --- There are no fraction part ---
    @Test
    void shouldConvertWholePositive() {
        Decimal9 m = Decimal9Converter.fromDouble(42.0);
        assertEquals(42L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldConvertWholeNegative() {
        Decimal9 m = Decimal9Converter.fromDouble(-42.0);
        assertEquals(-42L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldConvertOne() {
        Decimal9 m = Decimal9Converter.fromDouble(1.0);
        assertEquals(1L, m.units());
        assertEquals(0, m.nanos());
    }

    // --- There are no units (0 < |v| < 1) ---
    @Test
    void shouldConvertFractionOnlyPositive() {
        Decimal9 m = Decimal9Converter.fromDouble(0.5);
        assertEquals(0L, m.units());
        assertEquals(500_000_000, m.nanos());
    }

    @Test
    void shouldConvertFractionOnlyNegative() {
        Decimal9 m = Decimal9Converter.fromDouble(-0.5);
        assertEquals(0L, m.units());
        assertEquals(-500_000_000, m.nanos());
    }

    @Test
    void shouldConvertSmallFraction() {
        Decimal9 m = Decimal9Converter.fromDouble(0.000_000_001); // 1 nano
        assertEquals(0L, m.units());
        assertEquals(1, m.nanos());
    }

    @Test
    void shouldConvertSmallNegativeFraction() {
        Decimal9 m = Decimal9Converter.fromDouble(-0.000_000_001);
        assertEquals(0L, m.units());
        assertEquals(-1, m.nanos());
    }

    // scale = 1 (e = -1)
    @Test
    void shouldConvertOneDecimalPlace() {
        Decimal9 m = Decimal9Converter.fromDouble(3.5);
        assertEquals(3L, m.units());
        assertEquals(500_000_000, m.nanos());
    }

    // scale = 9 (e = -9) — edge case scale <= 9
    @Test
    void shouldConvertNineDecimalPlaces() {
        // 1.000000001 = 1 + 1 nano
        Decimal9 m = Decimal9Converter.fromDouble(1.000_000_001);
        assertEquals(1L, m.units());
        assertEquals(1, m.nanos());
    }

    // scale = 9, rem is at maximum capacity — check for no overflow during multiplication
    @Test
    void shouldConvertMaxNanosAtScaleNine() {
        // 1.999999999 -> units=1, nanos=999_999_999
        Decimal9 m = Decimal9Converter.fromDouble(1.999_999_999);
        assertEquals(1L, m.units());
        assertEquals(999_999_999, m.nanos());
    }

    // scale = 17 (e = -17) — scale > 9 branch in roundRemToNanos
    @Test
    void shouldConvertHighPrecisionFraction() {
        // 0.1 in IEEE 754: Decimal9(false, 1000000000000000, -16) or similar
        // we just verify the result is close to expected
        Decimal9 m = Decimal9Converter.fromDouble(0.1);
        assertEquals(0L, m.units());
        assertEquals(100_000_000, m.nanos());
    }

    @Test
    void shouldConvertHighPrecisionFractionWithRounding() {
        // 0.123456789123456789 -> rounded to 9 digits = 0.123456789
        Decimal9 m = Decimal9Converter.fromDouble(0.123_456_789_123_456_789);
        assertEquals(0L, m.units());
        assertEquals(123_456_789, m.nanos());
    }

    @Test
    void shouldHitRoundRemBranch_scaleLe9() {
        // value = 12.34 => s=1234, e=-2 => scale=2 <= 9
        // unitsAbs = 12, rem = 34 -> scale<=9 branch
        converter.encodeDouble(false, 1234L, -2);
        Decimal9 m = converter.result();
        assertEquals(12L, m.units());
        assertEquals(340_000_000, m.nanos());
    }

    @Test
    void shouldHitRoundRemBranch_scaleEq9() {
        // value = 1.000000001 => s=1_000_000_001, e=-9 => scale=9 <= 9
        // unitsAbs=1, rem=1 -> nanos=1
        converter.encodeDouble(false, 1_000_000_001L, -9);
        Decimal9 m = converter.result();
        assertEquals(1L, m.units());
        assertEquals(1, m.nanos());
    }

    @Test
    void shouldHitRoundRemBranch_scaleLe9_negative() {
        converter.encodeDouble(true, 1234L, -2);
        Decimal9 m = converter.result();
        assertEquals(-12L, m.units());
        assertEquals(-340_000_000, m.nanos());
    }

    @Test
    void shouldReturnZeroWhenScaleIsTooLargeInRoundFracToNanos_positive() {
        // scale = 28 -> d = 19 -> d >= POW10.length (19), return-0 branch
        converter.encodeDouble(false, 1L, -28);
        Decimal9 m = converter.result();
        assertEquals(0L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldReturnZeroWhenScaleIsTooLargeInRoundFracToNanos_positive2() {
        // scale = 28 -> d = 19 -> d >= POW10.length (19), return-0 branch
        Decimal9 m = Decimal9Converter.fromDouble(1e-28);
        assertEquals(0L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldReturnZeroWhenScaleIsTooLargeInRoundFracToNanos_negative() {
        // same path, but with negative sign
        converter.encodeDouble(true, 1L, -28);
        Decimal9 m = converter.result();
        assertEquals(0L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldNotReturnZeroAtBoundaryBeforeTooLargeScale() {
        // scale = 27 -> d = 18 (< 19), return-0 branch does not trigger
        converter.encodeDouble(false, 5L, -27);
        Decimal9 m = converter.result();
        assertEquals(0L, m.units());
        assertEquals(0, m.nanos()); // still zero because the value is tiny, but code path is different
    }

    @Test
    void shouldNotReturnZeroAtBoundaryBeforeTooLargeScale2() {
        // scale = 27 -> d = 18 (< 19), return-0 branch does not trigger
        Decimal9 m = Decimal9Converter.fromDouble(5e-27);
        assertEquals(0L, m.units());
        assertEquals(0, m.nanos()); // still zero because the value is tiny, but code path is different
    }

    @Test
    void shouldNotOverflowInRoundFracToNanos_largeSignificandScale19() {
        // Enter roundFracToNanos: scale >= 19
        // scale = 19 => d = 10 => div = 10_000_000_000
        // Value intentionally near Long.MAX_VALUE
        converter.encodeDouble(false, Long.MAX_VALUE, -19);
        Decimal9 m = converter.result();

        // Expect a positive and reasonable nanos value (not negative due to overflow)
        assertEquals(0L, m.units());
        assertEquals(922_337_204, m.nanos());
    }

    @Test
    void shouldRoundHalfUpAtBoundaryInRoundFracToNanos() {
        // scale = 19 => div = 10^10
        // s = div/2 => exactly 0.5 nano, should round up to 1 nano
        converter.encodeDouble(false, 5_000_000_000L, -19);
        Decimal9 m = converter.result();

        assertEquals(0L, m.units());
        assertEquals(1, m.nanos());
    }

    @Test
    void shouldRoundDownJustBelowHalfInRoundFracToNanos() {
        // just below 0.5 -> round down
        converter.encodeDouble(false, 4_999_999_999L, -19);
        Decimal9 m = converter.result();

        assertEquals(0L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldRoundHalfUpInRoundRemToNanos_scale10() {
        // scale=10 => div=10 in roundRemToNanos
        // value = 0.0000000005 => nanos = 1
        converter.encodeDouble(false, 5L, -10);
        Decimal9 m = converter.result();

        assertEquals(0L, m.units());
        assertEquals(1, m.nanos());
    }

    @Test
    void shouldRoundDownInRoundRemToNanos_scale10() {
        // value = 0.0000000004 => nanos = 0
        converter.encodeDouble(false, 4L, -10);
        Decimal9 m = converter.result();

        assertEquals(0L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldCarryFromFractionToUnits_withoutOverflowAtHalfUpBoundary() {
        // value = 0.99999999995 -> rounded to 9 digits = 1.000000000
        converter.encodeDouble(false, 9_999_999_999_5L, -11);
        Decimal9 m = converter.result();

        assertEquals(1L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldHandleVeryLargeSignificandAtScale1_withoutCarry() {
        // scale = 1 -> nanos = rem * 10^8, no carry happens
        long significand = 9_223_372_036_854_775_805L; // Long.MAX_VALUE - 2, rem=5
        converter.encodeDouble(false, significand, -1);
        Decimal9 m = converter.result();

        assertEquals(significand / 10, m.units());
        assertEquals(500_000_000, m.nanos());
    }

    @Test
    void shouldCarryNearLongLimitWithoutUnitsOverflow() {
        int scale = 10;
        long pow = 10_000_000_000L; // 10^scale

        // Take the maximum long significand whose remainder by pow is pow-1
        // (this gives round half-up -> 1_000_000_000 -> carry)
        // significand = k*pow + (pow-1) <= Long.MAX_VALUE
        long unitsAbs = (Long.MAX_VALUE - (pow - 1)) / pow;
        long significand = unitsAbs * pow + (pow - 1); // guaranteed <= Long.MAX_VALUE

        converter.encodeDouble(false, significand, -scale);
        Decimal9 m = converter.result();

        assertEquals(unitsAbs + 1, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldNotReachCarryPathWhenExponentIsNonNegative() {
        converter.encodeDouble(false, Long.MAX_VALUE, 0);
        Decimal9 m = converter.result();
        assertEquals(Long.MAX_VALUE, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldThrowOnOverflow() {
        assertThrows(ArithmeticException.class, () -> converter.encodeDouble(false, Long.MAX_VALUE, 1));
    }

    @Test
    void shouldEncodeExponent18() {
        converter.encodeDouble(false, 9L, 18);
        Decimal9 m = converter.result();
        assertEquals(9_000_000_000_000_000_000L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldThrowOnExponent18Overflow() {
        assertThrows(ArithmeticException.class, () -> converter.encodeDouble(false, 10L, 18));
    }

    @Test
    void shouldThrowOnExponent19() {
        assertThrows(ArithmeticException.class, () -> converter.encodeDouble(false, 1L, 19));
    }

    @Test
    void shouldRoundHalfUpAtScale27() {
        // scale = 27 -> d = 18: 0.5 nano rounds up to 1 nano
        converter.encodeDouble(false, 500_000_000_000_000_000L, -27);
        Decimal9 m = converter.result();
        assertEquals(0L, m.units());
        assertEquals(1, m.nanos());
    }

    @Test
    void shouldConvertWholePositive1() {
        converter.encodeDouble(false, 10000000000000000L, -16);
        Decimal9 m = converter.result();
        assertEquals(1L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldKeepUnitsAtScale18() {
        converter.encodeDouble(false, 1_000_000_000_000_000_000L, -18);
        Decimal9 m = converter.result();
        assertEquals(1L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldKeepUnitsAtScale18_negative() {
        converter.encodeDouble(true, 1_000_000_000_000_000_000L, -18);
        Decimal9 m = converter.result();
        assertEquals(-1L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldSplitLongMaxValueAtScale18() {
        // 9.223372036854775807 -> 9 units, 223372036.854... rounds up to 223372037 nanos
        converter.encodeDouble(false, Long.MAX_VALUE, -18);
        Decimal9 m = converter.result();
        assertEquals(9L, m.units());
        assertEquals(223_372_037, m.nanos());
    }

    @Test
    void shouldCarryNanosIntoUnitsAtScale18() {
        converter.encodeDouble(false, 1_999_999_999_999_999_999L, -18);
        Decimal9 m = converter.result();
        assertEquals(2L, m.units());
        assertEquals(0, m.nanos());
    }

    @Test
    void shouldKeepFractionBelowOneAtScale18() {
        converter.encodeDouble(false, 999_999_999_400_000_000L, -18);
        Decimal9 m = converter.result();
        assertEquals(0L, m.units());
        assertEquals(999_999_999, m.nanos());
    }

    @Test
    void shouldDecodeDecimalToDouble() {
        Decimal9 value = new Decimal9(12, 340_000_000);
        assertEquals(12.34d, Decimal9Converter.toDouble(value));
    }

    @Test
    void shouldDecodeLongMinWithoutOverflow() {
        Decimal9 value = new Decimal9(Long.MIN_VALUE, 0);
        assertEquals((double) Long.MIN_VALUE, Decimal9Converter.toDouble(value));
    }

    @Test
    void shouldIgnoreNanosForLongMinDecode() {
        Decimal9 value = new Decimal9(Long.MIN_VALUE, -999_999_999);
        assertEquals((double) Long.MIN_VALUE, Decimal9Converter.toDouble(value));
    }

    @Test
    void shouldDecodeUsingZeroFractionDigitsWhenCombinedSignificandOverflows() {
        Decimal9 value = new Decimal9(Long.MAX_VALUE, 999_999_999);
        double expected = Long.MAX_VALUE;

        assertEquals(expected, Decimal9Converter.toDouble(value));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void shouldDecodeAtMaxUnitsBoundaryForEachFractionDigit(int fractionDigits) {
        int nanos = 999_999_999;
        long pow = Schubfach.MathUtils.pow10(fractionDigits);
        int nanosDiv = (int) Schubfach.MathUtils.pow10(9 - fractionDigits);
        long nanosHead = nanos / nanosDiv;
        long units = (Long.MAX_VALUE - nanosHead) / pow;

        Decimal9 value = new Decimal9(units, nanos);
        long expectedSignificand = units * pow + nanosHead;

        double expectedDouble = Double.parseDouble(expectedSignificand + "E-" + fractionDigits);

        assertEquals(expectedDouble, Decimal9Converter.toDouble(value));

        if (fractionDigits < 9) {
            assertFalse(fits(units, nanos, fractionDigits + 1));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void shouldDecodeNegativeAtMaxUnitsBoundaryForEachFractionDigit(int fractionDigits) {
        int nanos = 999_999_999;
        long pow = Schubfach.MathUtils.pow10(fractionDigits);
        int nanosDiv = (int) Schubfach.MathUtils.pow10(9 - fractionDigits);
        long nanosHead = nanos / nanosDiv;
        long units = (Long.MAX_VALUE - nanosHead) / pow;

        Decimal9 value = new Decimal9(-units, -nanos);
        long expectedSignificand = units * pow + nanosHead;

        double expectedDouble = Double.parseDouble("-" + expectedSignificand + "E-" + fractionDigits);

        assertEquals(expectedDouble, Decimal9Converter.toDouble(value));

        if (fractionDigits < 9) {
            assertFalse(fits(units, nanos, fractionDigits + 1));
        }
    }

    @Test
    void shouldRejectMixedSignDecimalForDecode() {
        assertThrows(IllegalArgumentException.class, () -> new Decimal9(10, -1));
    }

    @Test
    void shouldRejectWrongPositiveNanos() {
        assertThrows(IllegalArgumentException.class, () -> new Decimal9(10, 1_000_000_000));
    }

    @Test
    void shouldRejectWrongNegativeNanos() {
        assertThrows(IllegalArgumentException.class, () -> new Decimal9(-10, Integer.MIN_VALUE));
    }

    private static boolean fits(long units, int nanos, int fractionDigits) {
        long pow = Schubfach.MathUtils.pow10(fractionDigits);
        int nanosDiv = (int) Schubfach.MathUtils.pow10(9 - fractionDigits);
        long nanosHead = nanos / nanosDiv;
        return units <= (Long.MAX_VALUE - nanosHead) / pow;
    }
}
