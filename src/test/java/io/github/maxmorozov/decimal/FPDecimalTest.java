package io.github.maxmorozov.decimal;

import io.github.maxmorozov.bigmath.BigMath;
import io.github.maxmorozov.bigmath.FPRounding;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static io.github.maxmorozov.bigmath.BigMath.toDoubleSlow;
import static io.github.maxmorozov.bigmath.FPRounding.toDoubleNanosHi;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FPDecimalTest {
    @Test
    void testSlowRoundingSmall() {
        FPDecimal decimal = new FPDecimal(false, 18905424454L, 569986343);
        double d = decimal.toDouble();
        double ref = Double.parseDouble("18905424454.569986343");
        assertEquals(ref, d);
    }

    @Test
    void testSlowRoundingBig() {
        FPDecimal decimal = new FPDecimal(false, 9195887603825644031L, 654254728);
        double d = decimal.toDouble();
        double ref = Double.parseDouble("9195887603825644031.654254728");
        assertEquals(ref, d);
    }

    @Test
    void testEdgeCases() {
        check(1, 0);
        check((1L << 28) - 1L, -1L);
    }

    static void check(long hi, long lo) {
        assertEquals(BigMath.toDoubleSlow(hi, lo, -9), toDoubleNanosHi(hi, lo));
    }

    static final BigInteger D = BigInteger.valueOf(1_953_125L);

    /**
     * q = (2m+1) << (drop-1), W = 5^9*q + extra
     */
    static long[] tie(long m, int drop, long extra) {
        BigInteger q = BigInteger.valueOf(m).shiftLeft(drop)
                .or(BigInteger.ONE.shiftLeft(drop - 1));
        BigInteger w = q.multiply(D).add(BigInteger.valueOf(extra));
        return new long[]{w.shiftRight(64).longValueExact(), w.longValue()}; // hi, lo
    }

    @Test
    void tiesDouble() {
        for (int drop = 1; drop <= 19; drop++) {
            for (long m : new long[]{1L << 52, (1L << 52) + 1, (1L << 53) - 1, (1L << 53) - 2}) {
                for (long extra : new long[]{0, 1, D.longValue() - 1}) {
                    long[] w = tie(m, drop, extra);
                    if (w[0] == 0) continue;                  // hi != 0 is required
                    assertEquals(
                            Double.doubleToRawLongBits(BigMath.toDoubleSlow(w[0], w[1], -9)),
                            Double.doubleToRawLongBits(toDoubleNanosHi(w[0], w[1])));
                }
            }
        }
    }

    @Test
    void tiesFloat() {
        for (int drop = 1; drop <= 48; drop++) {
            for (long m : new long[]{1L << 24, (1L << 24) + 1, (1L << 24) - 1, (1L << 24) - 2}) {
                for (long extra : new long[]{0, 1, D.longValue() - 1}) {
                    long[] w = tie(m, drop, extra);
                    if (w[0] == 0) continue;                  // hi != 0 is required
                    assertEquals(
                            Float.floatToIntBits(BigMath.toFloatSlow(w[0], w[1], -9)),
                            Float.floatToIntBits(FPRounding.toFloatNanosHi(w[0], w[1])));
                }
            }
        }
    }

    @Disabled
    @Test
    void testNanoRound() {
        Random rnd = new Random(7);
        for (int i = 0; i < 100_000_000; i++) {
            long hi = rnd.nextLong() >>> (rnd.nextInt(30));   // different lengths, hi != 0
            long lo = rnd.nextLong();
            if (hi == 0) continue;
            assertEquals(
                    Double.doubleToRawLongBits(toDoubleNanosHi(hi, lo)),
                    Double.doubleToRawLongBits(toDoubleSlow(hi, lo, -9))
            );
        }
    }

}
