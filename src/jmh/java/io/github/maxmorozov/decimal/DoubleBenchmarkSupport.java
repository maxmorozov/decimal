package io.github.maxmorozov.decimal;

import java.math.BigInteger;
import java.util.Random;

public class DoubleBenchmarkSupport {

    private static final BigInteger E9 = BigInteger.valueOf(1_000_000_000L);

    private static final BigInteger D = BigInteger.valueOf(1_953_125L);          // 5^9
    private static final BigInteger MAX_W = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(E9).add(BigInteger.valueOf(999_999_999L));

    // ------------------------------------------------------------------ data generation

    public static boolean isNegative(String kind, Random rnd) {
        return (kind.equals("mixed") || kind.equals("bank")) && rnd.nextBoolean();
    }

    /**
     * Returns {units, nanos}.
     */
    public static long[] next(String kind, Random rnd) {
        return switch (kind) {
            case "clinger" -> random(rnd, 1, 53, 0.0);
            case "lo" -> random(rnd, 54, 64, 0.0);
            case "hi_left" -> random(rnd, 65, 76, 0.0);
            case "hi_right" -> random(rnd, 77, 93, 0.0);
            case "hi_both" -> random(rnd, 65, 93, 0.0);
            case "integers" -> random(rnd, 31, 93, 1.0);
            case "ties" -> tie(rnd);
            case "mixed" -> {
                String[] kinds = {"clinger", "lo", "hi_left", "hi_right", "integers", "ties"};
                yield next(kinds[rnd.nextInt(kinds.length)], rnd);
            }
            case "bank" -> bank(rnd);
            default -> throw new IllegalArgumentException(kind);
        };
    }

    /**
     * Random W = units * 10^9 + nanos with the bit length in [minBits; maxBits].
     * With the probability integerShare the value is rounded down to nanos == 0.
     */
    private static long[] random(Random rnd, int minBits, int maxBits, double integerShare) {
        while (true) {
            int bits = minBits + rnd.nextInt(maxBits - minBits + 1);
            BigInteger w = new BigInteger(bits - 1, rnd).setBit(bits - 1);
            if (rnd.nextDouble() < integerShare) {
                w = w.divide(E9).multiply(E9);
            }
            if (w.signum() == 0 || w.compareTo(MAX_W) > 0) {
                continue;
            }
            return split(w);
        }
    }

    /**
     * W = 5^9 * q + extra, q = (2m + 1) * 2^(drop - 1) with a 53-bit m: W / 10^9 = q / 2^9 is exactly halfway
     * between two doubles when extra == 0, and one unit of W below/above that point otherwise.
     */
    private static long[] tie(Random rnd) {
        while (true) {
            long m = (1L << 52) + Math.floorMod(rnd.nextLong(), 1L << 52);
            int drop = 1 + rnd.nextInt(19);                       // q < 2^72
            BigInteger q = BigInteger.valueOf(m).shiftLeft(drop).or(BigInteger.ONE.shiftLeft(drop - 1));
            BigInteger w = q.multiply(D).add(BigInteger.valueOf(rnd.nextInt(3) - 1));
            if (w.compareTo(MAX_W) <= 0) {
                return split(w);
            }
        }
    }

    private static long[] split(BigInteger w) {
        BigInteger[] qr = w.divideAndRemainder(E9);
        return new long[]{qr[0].longValueExact(), qr[1].longValueExact()};
    }

    private static long[] bank(Random rnd) {
        int r = rnd.nextInt(100);
        if (r < 55) {                                         // FX rates and prices, 4..6 decimals
            long units = rnd.nextInt(r < 20 ? 3 : 200_000);
            int digits = 4 + rnd.nextInt(3);
            int scale = (int) Math.pow(10, 9 - digits);
            return new long[]{units, (long) rnd.nextInt((int) Math.pow(10, digits)) * scale};
        } else if (r < 95) {                                  // amounts with 2 decimals, up to ~3e10
            long units = Math.floorMod(rnd.nextLong(), 1L << (10 + rnd.nextInt(26)));
            return new long[]{units, rnd.nextInt(100) * 10_000_000L};
        } else {                                              // large notionals without decimals
            return new long[]{Math.floorMod(rnd.nextLong(), 1L << (30 + rnd.nextInt(33))), 0};
        }
    }
}