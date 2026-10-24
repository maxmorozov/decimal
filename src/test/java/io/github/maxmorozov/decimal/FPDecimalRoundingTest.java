package io.github.maxmorozov.decimal;

import io.github.maxmorozov.bigmath.BigMath;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Проверяет FPDecimal.toDouble()/toFloat() на значениях W * 10^-9 против двух независимых эталонов:
 * BigDecimal.doubleValue()/floatValue() и BigMath.toDoubleSlow()/toFloatSlow().
 * Каждое значение проверяется с обоими знаками и для double, и для float.
 */
class FPDecimalRoundingTest {

    private static final BigInteger E9 = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger D = BigInteger.valueOf(1_953_125L);          // 5^9
    private static final BigInteger MAX_W = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(E9).add(BigInteger.valueOf(999_999_999L));                 // units = MAX, nanos = 999999999

    private static final long SEED = 0x5EEDL;
    private static final int PER_BIT_LENGTH = 5_000;

    // ------------------------------------------------------------------ core check

    /**
     * W * 10^-9 -> double/float для обоих знаков. Значения вне диапазона FPDecimal пропускаются.
     */
    private static void check(BigInteger w) {
        if (w.signum() < 0 || w.compareTo(MAX_W) > 0) return;

        BigInteger[] qr = w.divideAndRemainder(E9);
        long units = qr[0].longValueExact();
        int nanos = qr[1].intValueExact();
        long hi = w.shiftRight(64).longValue();
        long lo = w.longValue();
        String ctx = String.format("W=%s units=%d nanos=%09d hi=%d lo=%d", w, units, nanos, hi, lo);

        BigDecimal exact = new BigDecimal(w).movePointLeft(9);
        double expD = exact.doubleValue();
        float expF = exact.floatValue();

        // сами эталоны должны совпадать между собой
        assertEquals(Double.doubleToRawLongBits(expD),
                Double.doubleToRawLongBits(BigMath.toDoubleSlow(hi, lo, -9)),
                () -> "oracles disagree (double): " + ctx);
        assertEquals(Float.floatToRawIntBits(expF),
                Float.floatToRawIntBits(BigMath.toFloatSlow(hi, lo, -9)),
                () -> "oracles disagree (float): " + ctx);

        for (boolean neg : new boolean[]{false, true}) {
            FPDecimal d = new FPDecimal(neg, units, nanos);
            double ed = neg ? -expD : expD;
            float ef = neg ? -expF : expF;
            assertEquals(Double.doubleToRawLongBits(ed), Double.doubleToRawLongBits(d.toDouble()),
                    () -> "toDouble neg=" + neg + " " + ctx);
            assertEquals(Float.floatToRawIntBits(ef), Float.floatToRawIntBits(d.toFloat()),
                    () -> "toFloat neg=" + neg + " " + ctx);
        }
    }

    private static BigInteger pow2(int k) {
        return BigInteger.ONE.shiftLeft(k);
    }

    // ------------------------------------------------------------------ tests

    /**
     * Случайные W для каждой длины 54..93 бита. Для 65..93 бит hi != 0, что даёт sh = -11..17
     * (в том числе sh == 0 при 76 битах и k == 0 в левом сдвиге).
     */
    @Test
    void randomByBitLength() {
        Random rnd = new Random(SEED);
        for (int bits = 54; bits <= 93; bits++) {
            for (int i = 0; i < PER_BIT_LENGTH; i++) {
                check(new BigInteger(bits - 1, rnd).setBit(bits - 1));
            }
        }
    }

    /**
     * Случайные units/nanos без привязки к длине W.
     */
    @Test
    void randomUnitsAndNanos() {
        Random rnd = new Random(SEED + 1);
        for (int i = 0; i < 200_000; i++) {
            long units = rnd.nextLong() >>> (1 + rnd.nextInt(63));
            int nanos = rnd.nextInt(1_000_000_000);
            check(BigInteger.valueOf(units).multiply(E9).add(BigInteger.valueOf(nanos)));
        }
    }

    /**
     * 2^b + {-2..2} для всех b: границы длин, переходы hi == 0 / hi != 0, sh == 0.
     */
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
        check(MAX_W);                                   // максимум W
        check(MAX_W.subtract(BigInteger.ONE));
        check(pow2(64));                                // hi = 1, lo = 0 (минимум для hi != 0)
        check(pow2(64).add(BigInteger.ONE));
        check(pow2(64).subtract(BigInteger.ONE));       // hi = 0, lo = -1
        check(pow2(53).add(BigInteger.ONE));            // первый W, не проходящий Clinger
        check(BigInteger.ZERO);
        check(BigInteger.ONE);
    }

    /**
     * Ничьи double: q = W / 5^9 = (2m+1) * 2^(drop-1), m 53-битное, четное и нечетное.
     * extra = 0 - настоящая ничья (решает четность m); остальные extra дают "чуть ниже"/"чуть выше"
     * (sticky от остатка деления на 5^9).
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

    /**
     * Ничьи float: m 24-битное, drop до 48 (q не длиннее 72 бит).
     */
    @Test
    void floatTies() {
        Random rnd = new Random(SEED + 3);
        for (int drop = 1; drop <= 48; drop++) {
            for (long m : mantissas(rnd, 24)) {
                tieFamily(m, drop);
            }
        }
    }

    /**
     * Перенос при округлении вверх (m == 2^p): q из одних единиц и соседи 2^k.
     */
    @Test
    void roundingCarry() {
        long[] extras = {-1, 0, 1, 1_953_124};
        for (int k = 2; k <= 72; k++) {
            BigInteger base = pow2(k);
            for (long dq : new long[]{-2, -1, 0, 1}) {
                BigInteger q = base.add(BigInteger.valueOf(dq));
                for (long extra : extras) {
                    check(q.multiply(D).add(BigInteger.valueOf(extra)));
                }
            }
        }
    }

    /**
     * Примеры из обсуждения с заранее известным результатом (double).
     */
    @Test
    void documentedTies() {
        // q = 2^53 + 1: ничья, m четное -> вниз
        assertEquals(0x1p44, new FPDecimal(false, 17592186044416L, 1_953_125).toDouble());
        // q = 2^53 + 3: ничья, m нечетное -> вверх
        assertEquals(0x1p44 + 0x1p-7, new FPDecimal(false, 17592186044416L, 5_859_375).toDouble());
        // ничья + остаток 1 (m четное) -> вверх за счет sticky
        assertEquals(0x1p44 + 0x1p-8, new FPDecimal(false, 17592186044416L, 1_953_126).toDouble());
        // ничья + остаток 1 (m нечетное) -> вверх
        assertEquals(0x1p44 + 0x1p-7, new FPDecimal(false, 17592186044416L, 5_859_376).toDouble());
        // знак
        assertEquals(-0x1p44, new FPDecimal(true, 17592186044416L, 1_953_125).toDouble());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Мантиссы ровно `precision` бит: граничные (четные/нечетные) + случайные.
     */
    private static long[] mantissas(Random rnd, int precision) {
        long lo = 1L << (precision - 1);
        long hi = 1L << precision;
        long[] r = new long[24];
        r[0] = lo;          // четное
        r[1] = lo + 1;      // нечетное
        r[2] = hi - 2;      // четное
        r[3] = hi - 1;      // нечетное, при округлении вверх дает перенос
        for (int i = 4; i < r.length; i++) {
            r[i] = lo + Math.floorMod(rnd.nextLong(), lo);
        }
        return r;
    }

    /**
     * q = (2m+1) * 2^(drop-1), W = 5^9 * q + extra, плюс точные соседи q +- 1.
     */
    private static void tieFamily(long m, int drop) {
        BigInteger q = BigInteger.valueOf(m).shiftLeft(drop).or(pow2(drop - 1));
        for (long extra : new long[]{-1, 0, 1, 1_953_124, 1_953_126}) {
            check(q.multiply(D).add(BigInteger.valueOf(extra)));
        }
        check(q.subtract(BigInteger.ONE).multiply(D));
        check(q.add(BigInteger.ONE).multiply(D));
    }
}
