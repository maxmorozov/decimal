package io.github.maxmorozov.decimal64;

import java.util.Random;

public class Decimal64FloatCheck {
    static long bad = 0, n = 0;

    static void check(long sig, int exp) {
        if (sig <= 0 || sig > Decimal64.MAX_SIG) return;
        float ref = Float.parseFloat(sig + "E" + exp);
        float el = Decimal64.toFloatEiselLemire(sig, exp);
        float sl = Decimal64.toFloatSlow(sig, exp);
        float full = Decimal64.toFloat(Decimal64.encode(false, sig, exp));
        n++;
        int r = Float.floatToRawIntBits(ref);
        if (r != Float.floatToRawIntBits(el) || r != Float.floatToRawIntBits(sl) || r != Float.floatToRawIntBits(full)) {
            if (bad++ < 20)
                System.out.println("MISMATCH " + sig + "E" + exp + " ref=" + ref + " el=" + el + " slow=" + sl + " full=" + full);
        }
    }

    public static void main(String[] a) {
        long MAX = Decimal64.MAX_SIG;
        check(16777217L, 0);                                  // 2^24 + 1: exact tie
        System.out.println("16777217 -> " + Decimal64.toFloat(Decimal64.encode(false, 16777217L, 0)));
        check(8100000000000000L, -15);
        check(MAX, -2);
        check(1, 38);
        check(34028235L, 31);
        check(34028236L, 31);
        check(1, -45);
        check(2, -45);
        check(7, -46);
        check(14, -46);
        check(1, -46);
        check(7, -47);
        check(1, -61);
        check(MAX, -61);
        check(11754944L, -45);                                // near Float.MIN_NORMAL

        Random r = new Random(777);
        for (int i = 0; i < 3_000_000; i++) {
            int bits = 1 + r.nextInt(54);
            long sig = (r.nextLong() >>> (64 - bits));
            if (sig > MAX) sig %= MAX;
            if (sig == 0) sig = 1;
            check(sig, -398 + r.nextInt(768));
        }
        for (int i = 0; i < 2_000_000; i++) {                 // float range edges
            long sig = 1 + (Math.abs(r.nextLong()) % MAX);
            int e = r.nextInt(3);
            check(sig, e == 0 ? -75 + r.nextInt(30) : e == 1 ? 15 + r.nextInt(30) : -62 + r.nextInt(6));
        }
        for (int i = 0; i < 1_000_000; i++) {                 // 25-bit odd mantissas times 2^j with exp 0: exact ties
            long m = ((1L << 24) | (r.nextInt(1 << 24))) | 1;
            int j = r.nextInt(29);
            check(m << j, 0);
        }
        for (int k = 1; k <= 25; k++) {                       // m * 2^-k as decimals: ties with negative exponent
            for (int i = 0; i < 40000; i++) {
                long m = ((1L << 24) | r.nextInt(1 << 24)) | 1;
                double p = m;
                for (int j = 0; j < k; j++) p *= 5;
                if (p < 9e15) {
                    long s = m;
                    for (int j = 0; j < k; j++) s *= 5;
                    check(s, -k);
                }
            }
        }
        for (long sig = 1; sig <= 2000; sig++) for (int e = -398; e <= 369; e++) check(sig, e);

        // special values
        System.out.println(Decimal64.toFloat(Decimal64.encode(true, 0, 0)) + " " + Decimal64.toFloat(0x7800000000000000L)
                + " " + Decimal64.toFloat(0xF800000000000000L) + " " + Decimal64.toFloat(Decimal64.NAN_BITS));
        System.out.println("checked=" + n + " mismatches=" + bad);
    }
}
