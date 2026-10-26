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
 * Float benchmark. Data sets are split by the code path of FPDecimal.toFloat(), not by the old
 * "one/two compute_float calls" categories, and are generated in random order (so the branch predictor
 * cannot memorize a short repeating sequence). The score is ns per value (@OperationsPerInvocation).
 * <p>
 * clinger  : hi == 0, lo <= 2^24            -> (float) lo / 1e9f, rounding code is not used
 * lo_mid   : hi == 0, 2^24 < lo < 2^63      -> toFloatNanosLo, left shift
 * lo_high  : hi == 0, lo >= 2^63            -> toFloatNanosLo, right shift by 1 bit
 * hi       : hi != 0 (W of 65..93 bits)     -> toFloatNanosHi
 * integers : nanos == 0                     -> zero remainder of the division by 5^9
 * ties     : nanos == 0, exactly halfway    -> round-half-even decision
 * mixed    : random mix of all of the above
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(3)
@State(Scope.Thread)
@OperationsPerInvocation(FPDecimalFloatBenchmark.N)
public class FPDecimalFloatBenchmark {

    static final int N = 262144;

    private static final BigInteger E9 = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger MAX_W = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(E9).add(BigInteger.valueOf(999_999_999L));

    @Param({"clinger", "lo_mid", "lo_high", "hi", "integers", "ties", "mixed", "bank"})
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
        float[] expected = new float[N];
        for (int i = 0; i < N; i++) {
            long[] v = next(kind, rnd);
            u[i] = v[0];
            n[i] = (int) v[1];
            // guard: do not measure a wrong implementation
            expected[i] = new BigDecimal(v[0]).add(BigDecimal.valueOf(v[1], 9)).floatValue();
        }
        for (int i = 0; i < N; i++) {
            ref[i] = new FPDecimal(false, u[i], n[i]);
            if (Float.floatToRawIntBits(ref[i].toFloat()) != Float.floatToRawIntBits(expected[i])) {
                throw new IllegalStateException("Wrong result v1 for units=" + u[i] + " nanos=" + n[i]);
            }
        }
        for (int i = 0; i < N; i++) {
            data_v3[i] = new FPDecimalV3(false, u[i], n[i]);
            if (Float.floatToRawIntBits(data_v3[i].toFloat()) != Float.floatToRawIntBits(expected[i])) {
                throw new IllegalStateException("Wrong result v3 for units=" + u[i] + " nanos=" + n[i]);
            }
        }
        for (int i = 0; i < N; i++) {
            data_v4[i] = new FPDecimalV4(false, u[i], n[i]);
            if (Float.floatToRawIntBits(data_v4[i].toFloat()) != Float.floatToRawIntBits(expected[i])) {
                throw new IllegalStateException("Wrong result v4 for units=" + u[i] + " nanos=" + n[i]);
            }
        }
        for (int i = 0; i < N; i++) {
            data_v5[i] = new FPDecimalV5(false, u[i], n[i]);
            if (Float.floatToRawIntBits(data_v5[i].toFloat()) != Float.floatToRawIntBits(expected[i])) {
                throw new IllegalStateException("Wrong result v5 for units=" + u[i] + " nanos=" + n[i]);
            }
        }

/*
        for (int i = 0; i < N; i++) {
            long[] v = next(kind, rnd);
            data_v3[i] = new FPDecimalV3(false, v[0], (int) v[1]);
            data_v4[i] = new FPDecimalV4(false, v[0], (int) v[1]);
            ref[i] = new FPDecimal(false, v[0], (int) v[1]);

            // guard: do not measure a wrong implementation
            float expected = new BigDecimal(v[0]).add(BigDecimal.valueOf(v[1], 9)).floatValue();
            if (Float.floatToRawIntBits(data_v3[i].toFloat()) != Float.floatToRawIntBits(expected)) {
                throw new IllegalStateException("Wrong result v3 for units=" + v[0] + " nanos=" + v[1]);
            }
            if (Float.floatToRawIntBits(data_v4[i].toFloat()) != Float.floatToRawIntBits(expected)) {
                throw new IllegalStateException("Wrong result v4 for units=" + v[0] + " nanos=" + v[1]);
            }
        }
*/
    }

    @Benchmark
    public void toFloat_V1(Blackhole blackhole) {
        for (FPDecimal d : ref) {
            blackhole.consume(d.toFloat());
        }
    }

    @Benchmark
    public void toFloat_V3(Blackhole blackhole) {
        for (FPDecimalV3 d : data_v3) {
            blackhole.consume(d.toFloat());
        }
    }

    @Benchmark
    public void toFloat_V4(Blackhole blackhole) {
        for (FPDecimalV4 d : data_v4) {
            blackhole.consume(d.toFloat());
        }
    }

    @Benchmark
    public void toFloat_V5(Blackhole blackhole) {
        for (FPDecimalV5 d : data_v5) {
            blackhole.consume(d.toFloat());
        }
    }

    // ------------------------------------------------------------------ data generation

    /**
     * Returns {units, nanos}.
     */
    static long[] next(String kind, Random rnd) {
        return switch (kind) {
            case "clinger" -> random(rnd, 1, 24, 0.0);
            case "lo_mid" -> random(rnd, 25, 63, 0.0);
            case "lo_high" -> random(rnd, 64, 64, 0.0);
            case "hi" -> random(rnd, 65, 93, 0.0);
            case "integers" -> random(rnd, 31, 93, 1.0);
            case "ties" -> tie(rnd);
            case "mixed" -> {
                String[] kinds = {"clinger", "lo_mid", "lo_high", "hi", "integers", "ties"};
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
            BigInteger[] qr = w.divideAndRemainder(E9);
            return new long[]{qr[0].longValueExact(), qr[1].longValueExact()};
        }
    }

    /**
     * Integer units = (2m + 1) << (k - 1) with a 24-bit m: exactly halfway between two floats.
     */
    private static long[] tie(Random rnd) {
        long m = (1L << 23) + rnd.nextInt(1 << 23);
        int k = 1 + rnd.nextInt(38);                  // units < 2^62
        return new long[]{(2 * m + 1) << (k - 1), 0};
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

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(FPDecimalFloatBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
