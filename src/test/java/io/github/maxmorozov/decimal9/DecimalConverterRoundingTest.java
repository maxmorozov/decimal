package io.github.maxmorozov.decimal9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Tests Decimal9Converter.toDouble() for values W * 10^-9 against reference: BigDecimal.doubleValue(). Each value is
 * tested with both signs except zeros.
 */
class DecimalConverterRoundingTest {

    private static final BigInteger E9 = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger D = BigInteger.valueOf(1_953_125L); // 5^9
    private static final BigInteger MAX_W = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(E9)
            .add(BigInteger.valueOf(999_999_999L)); // units = MAX, nanos = 999999999

    private static final long SEED = 0x5EEDL;
    private static final int PER_BIT_LENGTH = 5_000;

    // ------------------------------------------------------------------ core check

    /**
     * Converts W * 10^-9 to double/float for both signs except zeros. Values outside the {@link Decimal9} range are
     * skipped.
     */
    private static void check(BigInteger w) {
        if (w.signum() < 0 || w.compareTo(MAX_W) > 0) {
            return;
        }

        BigInteger[] qr = w.divideAndRemainder(E9);
        long units = qr[0].longValueExact();
        int nanos = qr[1].intValueExact();
        long hi = w.shiftRight(64).longValue();
        long lo = w.longValue();
        String ctx = String.format("W=%s units=%d nanos=%09d hi=%d lo=%d", w, units, nanos, hi, lo);

        BigDecimal exact = new BigDecimal(w).movePointLeft(9);
        double expD = exact.doubleValue();

        for (boolean neg : getSigns(units, nanos)) {
            Decimal9 d = makeDecimal(neg, units, nanos);
            double ed = neg ? -expD : expD;
            assertEquals(
                    Double.doubleToRawLongBits(ed),
                    Double.doubleToRawLongBits(Decimal9Converter.toDouble(d)),
                    () -> "toDouble neg=" + neg + " " + ctx);
        }
    }

    private static boolean[] getSigns(long units, long nanos) {
        if (units == 0 && nanos == 0) {
            return new boolean[] {false};
        }
        return new boolean[] {false, true};
    }

    private static Decimal9 makeDecimal(boolean neg, long units, int nanos) {
        return new Decimal9(neg ? -units : units, neg ? -nanos : nanos);
    }

    private static BigInteger pow2(int k) {
        return BigInteger.ONE.shiftLeft(k);
    }

    // ------------------------------------------------------------------ tests

    /**
     * Random W values for each length from 54 to 93 bits. For 65 to 93 bits, hi != 0, resulting in sh = -11..17
     * (including sh == 0 at 76 bits and k == 0 in the left shift).
     */
    @Test
    void randomByBitLength() {
        Random rnd = new Random(SEED);
        for (int bits = 25; bits <= 93; bits++) {
            for (int i = 0; i < PER_BIT_LENGTH; i++) {
                check(new BigInteger(bits - 1, rnd).setBit(bits - 1));
            }
        }
    }

    /** lo >= 2^63: the bit lost by shifting by 1 turns a tie into "slightly above". */
    @Test
    void floatLoStickyFromShift() {
        Random rnd = new Random(SEED + 4);
        for (int drop = 18; drop <= 19; drop++) {
            for (long m : mantissas(rnd, 24)) {
                BigInteger q = BigInteger.valueOf(m).shiftLeft(drop).or(pow2(drop - 1));
                BigInteger w = q.multiply(D); // W' = 5^9 * q, 2^62 <= W' < 2^63
                if (w.bitLength() != 63) {
                    continue;
                }
                check(w.shiftLeft(1).or(BigInteger.ONE)); // W = 2W' + 1, 64 bits, lo < 0
                check(w.shiftLeft(1)); // exact tie
                check(w.shiftLeft(1).subtract(BigInteger.ONE)); // slightly below
            }
        }
    }

    @Test
    void loBoundaries() {
        for (BigInteger w : new BigInteger[] {
            pow2(63).subtract(BigInteger.ONE),
            pow2(63),
            pow2(63).add(BigInteger.ONE),
            pow2(62),
            pow2(62).subtract(BigInteger.ONE),
            pow2(64).subtract(BigInteger.ONE)
        }) {
            check(w);
        }
    }

    /** Random units/nanos independent of the length of W. */
    @Test
    void randomUnitsAndNanos() {
        Random rnd = new Random(SEED + 1);
        for (int i = 0; i < 200_000; i++) {
            long units = rnd.nextLong() >>> (1 + rnd.nextInt(63));
            int nanos = rnd.nextInt(1_000_000_000);
            check(BigInteger.valueOf(units).multiply(E9).add(BigInteger.valueOf(nanos)));
        }
    }

    /** 2^b + {-2..2} for all b: length boundaries, hi == 0 / hi != 0 transitions, and sh == 0. */
    @Test
    void powerOfTwoBoundaries() {
        for (int b = 1; b <= 93; b++) {
            for (int delta = -2; delta <= 2; delta++) {
                check(pow2(b).add(BigInteger.valueOf(delta)));
            }
        }
    }

    @Test
    void extremes() {
        check(MAX_W); // maximum W
        check(MAX_W.subtract(BigInteger.ONE));
        check(pow2(64)); // hi = 1, lo = 0 (minimum for hi != 0)
        check(pow2(64).add(BigInteger.ONE));
        check(pow2(64).subtract(BigInteger.ONE)); // hi = 0, lo = -1
        check(pow2(53).add(BigInteger.ONE)); // first W that does not pass Clinger
        check(BigInteger.ZERO);
        check(BigInteger.ONE);
    }

    /**
     * Double ties: q = W / 5^9 = (2m+1) * 2^(drop-1), where m is a 53-bit even or odd value. extra = 0 is an exact tie
     * (m parity decides); other extra values produce "slightly below"/"slightly above" (sticky from the remainder after
     * division by 5^9).
     */
    @Test
    void doubleTies() {
        Random rnd = new Random(SEED + 2);
        for (int drop = 1; drop <= 19; drop++) {
            for (long m : mantissas(rnd, 53)) {
                tieFamily(m, drop);
            }
        }
    }

    /** Float ties: m is 24-bit, and drop is at most 48 (q is no longer than 72 bits). */
    @Test
    void floatTies() {
        Random rnd = new Random(SEED + 3);
        for (int drop = 1; drop <= 48; drop++) {
            for (long m : mantissas(rnd, 24)) {
                tieFamily(m, drop);
            }
        }
    }

    /** Carry when rounding up (m == 2^p): q consisting entirely of ones and neighbors of 2^k. */
    @Test
    void roundingCarry() {
        long[] extras = {-1, 0, 1, 1_953_124};
        for (int k = 2; k <= 72; k++) {
            BigInteger base = pow2(k);
            for (long dq : new long[] {-2, -1, 0, 1}) {
                BigInteger q = base.add(BigInteger.valueOf(dq));
                for (long extra : extras) {
                    check(q.multiply(D).add(BigInteger.valueOf(extra)));
                }
            }
        }
    }

    /** Examples from the discussion with a known result (double). */
    @Test
    void documentedTies() {
        // q = 2^53 + 1: tie, m is even -> round down
        assertEquals(0x1p44, Decimal9Converter.toDouble(makeDecimal(false, 17592186044416L, 1_953_125)));
        // q = 2^53 + 3: tie, m is odd -> round up
        assertEquals(0x1p44 + 0x1p-7, Decimal9Converter.toDouble(makeDecimal(false, 17592186044416L, 5_859_375)));
        // tie + remainder 1 (m is even) -> round up due to sticky
        assertEquals(0x1p44 + 0x1p-8, Decimal9Converter.toDouble(makeDecimal(false, 17592186044416L, 1_953_126)));
        // tie + remainder 1 (m is odd) -> round up
        assertEquals(0x1p44 + 0x1p-7, Decimal9Converter.toDouble(makeDecimal(false, 17592186044416L, 5_859_376)));
        // sign
        assertEquals(-0x1p44, Decimal9Converter.toDouble(makeDecimal(true, 17592186044416L, 1_953_125)));
    }

    @Test
    void normalizationInvariants() {
        BigInteger d = BigInteger.valueOf(1_953_125L);
        assertEquals(42, pow2(62).divide(d).bitLength()); // float lo: q = 42 bits ...
        assertEquals(43, pow2(63).subtract(BigInteger.ONE).divide(d).bitLength()); // ... or 43

        for (int[] c : new int[][] {{53, 75}, {24, 46}}) { // {precision, N}, N = precision + 22
            int precision = c[0], n = c[1];
            BigInteger lo = BigInteger.ONE.shiftLeft(n - 1).divide(d);
            BigInteger hi = BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE).divide(d);
            assertEquals(precision + 1, lo.bitLength()); // rounding bit is present
            assertEquals(precision + 2, hi.bitLength()); // q has two extra bits
        }
        assertTrue(BigInteger.ONE.shiftLeft(46).bitLength() <= 63); // float: W' fits in long
    }

    @Test
    void floatNormalizationInvariants() {
        BigInteger d = BigInteger.valueOf(1_953_125L);
        BigInteger wMin = BigInteger.ONE.shiftLeft(62);
        BigInteger wMax = BigInteger.ONE.shiftLeft(63).subtract(BigInteger.ONE);
        assertEquals(42, wMin.divide(d).bitLength()); // Q_MIN_BITS
        assertEquals(43, wMax.divide(d).bitLength()); // Q_MIN_BITS + 1
    }

    /**
     * This test checks that {@code toDoubleNanosBranchless} calculates h0 << (64 - t) correctly.
     * If someone changes the implementation to use L = 22 instead of L = 23, the test will fail
     * because the shift would be (h0 << 1) << (63 - t) instead of h0 << (64 - t).
     * <p>
     * W of 54 bits is normalized by sh = -22, which gives the right shift t = 0 if the pre-shift L is 22: then
     * {@code h0 << (64 - t)} is {@code h0 << 0} in Java and adds the high bits of W (about 2^12) to W'. Here W' lies
     * 2^(drop-1) below a tie T = (2m+1) * 2^(drop-1) * 5^9, so that error pushes it above T and the result rounds up.
     */
    @Test
    void shortestGeneralPathJustBelowTie() {
        Random rnd = new Random(SEED + 6);
        for (int drop = 2; drop <= 3; drop++) {
            int k = 23 - drop; // W' = W * 2^22 = T - 2^(drop-1)  <=>  (2m+1) * 5^9 == 1 (mod 2^k)
            BigInteger oddLow = D.modInverse(pow2(k));
            int checked = 0;
            for (int i = 0; i < 2_000; i++) {
                BigInteger odd =
                        new BigInteger(54 - k, rnd).setBit(53 - k).shiftLeft(k).add(oddLow);
                BigInteger w = odd.multiply(D)
                        .shiftLeft(drop - 1)
                        .subtract(pow2(drop - 1))
                        .shiftRight(22);
                BigInteger q = w.shiftLeft(22).divide(D);
                if (w.bitLength() != 54 || q.bitLength() != 53 + drop) {
                    continue;
                }
                check(w);
                checked++;
            }
            assertTrue(checked > 100, "drop=" + drop + " checked=" + checked);
        }
    }

    /** nanos == 0: W is divisible by 5^9, the remainder is zero, and sticky comes only from the shift. */
    @Test
    void integerAmounts() {
        Random rnd = new Random(SEED + 5);
        for (int i = 0; i < 200_000; i++) {
            long units = rnd.nextLong() >>> (1 + rnd.nextInt(63));
            check(BigInteger.valueOf(units).multiply(E9));
            check(BigInteger.valueOf(units).multiply(E9).add(BigInteger.valueOf(rnd.nextInt(1000)))); // nearby
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Mantissas with exactly `precision` bits: boundary (even/odd) and random values. */
    private static long[] mantissas(Random rnd, int precision) {
        long lo = 1L << (precision - 1);
        long hi = 1L << precision;
        long[] r = new long[24];
        r[0] = lo; // even
        r[1] = lo + 1; // odd
        r[2] = hi - 2; // even
        r[3] = hi - 1; // odd; rounding up produces a carry
        for (int i = 4; i < r.length; i++) {
            r[i] = lo + Math.floorMod(rnd.nextLong(), lo);
        }
        return r;
    }

    /** q = (2m+1) * 2^(drop-1), W = 5^9 * q + extra, plus exact neighbors q +- 1. */
    private static void tieFamily(long m, int drop) {
        BigInteger q = BigInteger.valueOf(m).shiftLeft(drop).or(pow2(drop - 1));
        for (long extra : new long[] {-1, 0, 1, 1_953_124, 1_953_126}) {
            check(q.multiply(D).add(BigInteger.valueOf(extra)));
        }
        check(q.subtract(BigInteger.ONE).multiply(D));
        check(q.add(BigInteger.ONE).multiply(D));
    }
}
