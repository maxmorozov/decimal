package io.github.maxmorozov.decimal;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Double benchmark. Data sets are split by the code path of FPDecimal.toDouble() and are generated in random
 * order (no short repeating sequence for the branch predictor to memorize). The score is ns per value.
 * <p>
 * clinger  : hi == 0, lo <= 2^53              -> (double) lo / 1e9, rounding code is not used (baseline)
 * lo       : hi == 0, 2^53 < lo < 2^64        -> toDoubleNanosLo (or compute_float, whatever FPDecimal calls)
 * hi_left  : W of 65..76 bits                 -> toDoubleNanosHi, normalization shifts left (sh <= 0)
 * hi_right : W of 77..93 bits                 -> toDoubleNanosHi, normalization shifts right (sh > 0)
 * hi_both  : W of 65..93 bits                 -> toDoubleNanosHi, the sign of sh is random (unpredictable branch)
 * integers : nanos == 0                       -> zero remainder of the division by 5^9
 * ties     : W = 5^9 * q + {-1, 0, 1}         -> exactly halfway, or one unit away from it
 * mixed    : random mix of all of the above, half of the values are negative
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(3)
@State(Scope.Thread)
@OperationsPerInvocation(FPDecimalDoubleBenchmark.N)
public class FPDecimalDoubleBenchmark {

    static final int N = 262144;

    private static final BigInteger E9 = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger D = BigInteger.valueOf(1_953_125L);          // 5^9
    private static final BigInteger MAX_W = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(E9).add(BigInteger.valueOf(999_999_999L));

    @Param({"clinger", "lo", "hi_left", "hi_right", "hi_both", "integers", "ties", "mixed"})
    private String kind;

    private FPDecimalV3[] data_v3;
    private FPDecimalV4[] data_v4;
    private FPDecimalV5[] data_v5;
    private FPDecimal[] ref;

    @Setup
    public void setup() {
        Random rnd = new Random(42);
        data_v3 = new FPDecimalV3[N];
        data_v4 = new FPDecimalV4[N];
        data_v5 = new FPDecimalV5[N];
        ref = new FPDecimal[N];

        long[] u = new long[N];
        int[] n = new int[N];
        boolean[] neg = new boolean[N];
        double[] expected = new double[N];
        for (int i = 0; i < N; i++) {
            long[] v = next(kind, rnd);
            u[i] = v[0];
            n[i] = (int) v[1];
            // "mixed" also contains negative values: the sign handling is a data-dependent branch,
            // and a profile without negatives compiles it into an uncommon trap
            neg[i] = kind.equals("mixed") && rnd.nextBoolean();
            // guard: do not measure a wrong implementation
            expected[i] = new BigDecimal(v[0]).add(BigDecimal.valueOf(v[1], 9)).doubleValue();
            if (neg[i]) {
                expected[i] = -expected[i];
            }
        }
        for (int i = 0; i < N; i++) {
            ref[i] = new FPDecimal(neg[i], u[i], n[i]);
            if (Double.doubleToRawLongBits(ref[i].toDouble()) != Double.doubleToRawLongBits(expected[i])) {
                throw new IllegalStateException("Wrong result v1 for units=" + u[i] + " nanos=" + n[i]);
            }
        }
        for (int i = 0; i < N; i++) {
            data_v3[i] = new FPDecimalV3(neg[i], u[i], n[i]);
            if (Double.doubleToRawLongBits(data_v3[i].toDouble()) != Double.doubleToRawLongBits(expected[i])) {
                throw new IllegalStateException("Wrong result v3 for units=" + u[i] + " nanos=" + n[i]);
            }
        }
        for (int i = 0; i < N; i++) {
            data_v4[i] = new FPDecimalV4(neg[i], u[i], n[i]);
            if (Double.doubleToRawLongBits(data_v4[i].toDouble()) != Double.doubleToRawLongBits(expected[i])) {
                throw new IllegalStateException("Wrong result v4 for units=" + u[i] + " nanos=" + n[i]);
            }
        }
        for (int i = 0; i < N; i++) {
            data_v5[i] = new FPDecimalV5(neg[i], u[i], n[i]);
            if (Double.doubleToRawLongBits(data_v5[i].toDouble()) != Double.doubleToRawLongBits(expected[i])) {
                throw new IllegalStateException("Wrong result v5 for units=" + u[i] + " nanos=" + n[i]);
            }
        }

/*
        for (int i = 0; i < N; i++) {
            long[] v = next(kind, rnd);
            // "mixed" also contains negative values: the sign handling is a data-dependent branch,
            // and a profile without negatives compiles it into an uncommon trap
            boolean neg = kind.equals("mixed") && rnd.nextBoolean();
            data_v3[i] = new FPDecimalV3(neg, v[0], (int) v[1]);
            data_v4[i] = new FPDecimalV4(neg, v[0], (int) v[1]);
            ref[i] = new FPDecimal(neg, v[0], (int) v[1]);

            // guard: do not measure a wrong implementation
            double expected = new BigDecimal(v[0]).add(BigDecimal.valueOf(v[1], 9)).doubleValue();
            if (neg) {
                expected = -expected;
            }
            if (Double.doubleToRawLongBits(ref[i].toDouble()) != Double.doubleToRawLongBits(expected)) {
                throw new IllegalStateException("Wrong result v1 for units=" + v[0] + " nanos=" + v[1]);
            }
            if (Double.doubleToRawLongBits(data_v3[i].toDouble()) != Double.doubleToRawLongBits(expected)) {
                throw new IllegalStateException("Wrong result v3 for units=" + v[0] + " nanos=" + v[1]);
            }
            if (Double.doubleToRawLongBits(data_v4[i].toDouble()) != Double.doubleToRawLongBits(expected)) {
                throw new IllegalStateException("Wrong result v4 for units=" + v[0] + " nanos=" + v[1]);
            }
        }
*/
    }

    @Benchmark
    public void toDouble_V1(Blackhole blackhole) {
        for (FPDecimal d : ref) {
            blackhole.consume(d.toDouble());
        }
    }

    @Benchmark
    public void toDouble_V3(Blackhole blackhole) {
        for (FPDecimalV3 d : data_v3) {
            blackhole.consume(d.toDouble());
        }
    }

    @Benchmark
    public void toDouble_V4(Blackhole blackhole) {
        for (FPDecimalV4 d : data_v4) {
            blackhole.consume(d.toDouble());
        }
    }

    @Benchmark
    public void toDouble_V5(Blackhole blackhole) {
        for (FPDecimalV5 d : data_v5) {
            blackhole.consume(d.toDouble());
        }
    }

    // ------------------------------------------------------------------ data generation

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

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(FPDecimalDoubleBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
