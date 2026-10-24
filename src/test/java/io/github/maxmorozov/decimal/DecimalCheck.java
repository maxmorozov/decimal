package io.github.maxmorozov.decimal;

import io.github.maxmorozov.bigmath.BigMath;

import java.math.BigInteger;
import java.util.Random;

public class DecimalCheck {
    static long bad = 0, n = 0;

    static String str(boolean neg, long units, int nanos) {
        return (neg ? "-" : "") + units + "." + String.format("%09d", nanos);
    }

    static void check(boolean neg, long units, int nanos) {
        FPDecimal d = new FPDecimal(neg, units, nanos);
        String s = str(neg, units, nanos);
        double refD = Double.parseDouble(s);
        float refF = Float.parseFloat(s);
        double gotD = d.toDouble();
        float gotF = d.toFloat();
        n++;
        if (Double.doubleToRawLongBits(refD) != Double.doubleToRawLongBits(gotD)
                || Float.floatToRawIntBits(refF) != Float.floatToRawIntBits(gotF)) {
            if (bad++ < 20)
                System.out.println("MISMATCH " + s + " refD=" + refD + " gotD=" + gotD + " refF=" + refF + " gotF=" + gotF);
        }
        // direct test of the exact 128-bit fallback
        BigInteger W = BigInteger.valueOf(units).multiply(BigInteger.valueOf(1_000_000_000L)).add(BigInteger.valueOf(nanos));
        if (W.signum() > 0) {
            long hi = W.shiftRight(64).longValue(), lo = W.longValue();
            double sd = BigMath.toDoubleSlow(hi, lo, -9);
            float sf = BigMath.toFloatSlow(hi, lo, -9);
            if (Double.doubleToRawLongBits(sd) != Double.doubleToRawLongBits(Math.abs(refD))
                    || Float.floatToRawIntBits(sf) != Float.floatToRawIntBits(Math.abs(refF))) {
                if (bad++ < 20)
                    System.out.println("SLOW128 MISMATCH " + s + " " + sd + " vs " + refD + " / " + sf + " vs " + refF);
            }
        }
    }

    public static void main(String[] a) {
        Random r = new Random(2024);
        check(false, 0, 0);
        check(true, 0, 0);
        check(false, 0, 1);
        check(false, 1, 0);
        check(false, 0, 999_999_999);
        check(false, Long.MAX_VALUE, 999_999_999);
        check(false, Long.MAX_VALUE, 0);
        check(false, 9007199254740993L, 0);                         // 2^53 + 1: exact tie for double
        check(false, 16777217L, 0);                                  // 2^24 + 1: exact tie for float
        System.out.println("9007199254740993 -> " + new FPDecimal(false, 9007199254740993L, 0).toDouble());
        System.out.println("8.1 -> " + new FPDecimal(false, 8, 100_000_000).toDouble() + " / " + new FPDecimal(false, 8, 100_000_000).toFloat());

        for (int i = 0; i < 3_000_000; i++) {                        // random magnitudes
            int bits = r.nextInt(64);
            long units = bits == 0 ? 0 : (r.nextLong() >>> (64 - bits));
            int nanos = switch (r.nextInt(4)) {
                case 0 -> 0;
                case 1 -> 999_999_999;
                default -> r.nextInt(1_000_000_000);
            };
            check(r.nextBoolean(), units, nanos);
        }
        for (int i = 0; i < 1_500_000; i++) {                        // exact double ties: integer midpoints, units >= 2^53
            int k = 1 + r.nextInt(10);
            long m = (1L << 52) | (r.nextLong() >>> 12);
            long units = (m << k) | (1L << (k - 1));
            check(false, units, 0);
            check(false, units - 1, 999_999_999);
            check(false, units + 1, 0); // neighbours
        }
        for (int i = 0; i < 1_500_000; i++) {                        // exact float ties: odd 25-bit * 2^j
            long m = (1L << 24) | r.nextInt(1 << 24) | 1;
            int j = r.nextInt(38);
            check(false, m << j, 0);
        }
        for (int i = 0; i < 500_000; i++) {                          // small values, all nanos patterns
            check(false, r.nextInt(100_000_000), r.nextInt(1_000_000_000));
        }
        System.out.println("checked=" + n + " mismatches=" + bad);
    }
}
