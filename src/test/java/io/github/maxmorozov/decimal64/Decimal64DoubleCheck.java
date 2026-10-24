package io.github.maxmorozov.decimal64;

import java.util.Random;

public class Decimal64DoubleCheck {
    static long bad = 0, n = 0;

    static void check(long sig, int exp) {
        if (sig <= 0) return;
        double ref = Double.parseDouble(sig + "E" + exp);
        double el = Decimal64.toDoubleEiselLemire(sig, exp);
        double sl = Decimal64.toDoubleSlow(sig, exp);
        double full = Decimal64.toDouble(Decimal64.encode(false, sig, exp));
        n++;
        if (Double.doubleToRawLongBits(ref) != Double.doubleToRawLongBits(el)
                || Double.doubleToRawLongBits(ref) != Double.doubleToRawLongBits(sl)
                || Double.doubleToRawLongBits(ref) != Double.doubleToRawLongBits(full)) {
            if (bad++ < 20)
                System.out.println("MISMATCH " + sig + "E" + exp + " ref=" + ref + " el=" + el + " slow=" + sl + " full=" + full);
        }
    }

    public static void main(String[] a) {
        long MAX = 9_999_999_999_999_999L;
        // the case reported from Boost
        check(9007199254740993L, 0);
        System.out.println("9007199254740993 -> " + Decimal64.toDouble(Decimal64.encode(false, 9007199254740993L, 0)));
        check(8100000000000000L, -15);
        check(MAX, -2);
        check(1, 30);
        check(1_234_567_890_123_456L, -30);
        check(17976931348623157L / 10, 293);   // near Double.MAX_VALUE
        check(1797693134862315L, 293);
        check(1797693134862316L, 293);
        check(2225073858507201L, -323);        // near MIN_NORMAL
        check(4940656458412465L, -339);        // near MIN_VALUE
        check(1, -324);
        check(2, -324);
        check(3, -324);
        check(5, -324);
        check(1, -325);
        check(25, -326);

        Random r = new Random(12345);
        // random sig/exp over the full decimal64 range
        for (int i = 0; i < 3_000_000; i++) {
            int bits = 1 + r.nextInt(54);
            long sig = (r.nextLong() >>> (64 - bits));
            if (sig > MAX) sig = sig % MAX;
            if (sig == 0) sig = 1;
            int exp = -398 + r.nextInt(768);
            check(sig, exp);
        }
        // concentrated around the double range edges
        for (int i = 0; i < 2_000_000; i++) {
            long sig = 1 + (Math.abs(r.nextLong()) % MAX);
            int exp = r.nextBoolean() ? -345 + r.nextInt(40) : 290 + r.nextInt(25);
            check(sig, exp);
        }
        // exact halfway points: odd sig in [2^53, 1e16), exp 0; and small positive exps
        for (int i = 0; i < 1_000_000; i++) {
            long sig = (1L << 53) + (Math.abs(r.nextLong()) % (MAX - (1L << 53)));
            check(sig | 1, 0);
        }
        // exact binary fractions (ties are possible with negative exps): m * 2^-k as decimals
        for (int k = 1; k <= 60; k++) {
            for (int i = 0; i < 20000; i++) {
                long m = (1L << 52) + (Math.abs(r.nextLong()) % (1L << 52));
                m = (m << 1) | 1;                           // 54-bit odd -> midpoint
                // m * 2^-k = m * 5^k * 10^-k -- only usable if m*5^k < 1e16
                double p = m;
                for (int j = 0; j < k; j++) p *= 5;
                if (p < 9e15) {
                    long s = m;
                    for (int j = 0; j < k; j++) s *= 5;
                    check(s, -k);
                }
            }
        }
        // small sig, all exponents
        for (long sig = 1; sig <= 2000; sig++) for (int e = -398; e <= 369; e++) check(sig, e);

        System.out.println("checked=" + n + " mismatches=" + bad);
    }
}
