package io.github.maxmorozov.decimal;

import io.github.maxmorozov.decimal9.Decimal9;
import io.github.maxmorozov.decimal9.Decimal9Converter;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static io.github.maxmorozov.decimal.DoubleBenchmarkSupport.isNegative;
import static io.github.maxmorozov.decimal.DoubleBenchmarkSupport.next;

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
@OperationsPerInvocation(Decimal9Benchmark.N)
public class Decimal9Benchmark {

    static final int N = 262144;

    @Param({"clinger", "lo", "hi_left", "hi_right", "hi_both", "integers", "ties", "mixed", "bank"})
    private String kind;

    private Decimal9[] data;

    @Setup
    public void setup() {
        Random rnd = new Random(42);
        data = new Decimal9[N];

        long[] u = new long[N];
        int[] n = new int[N];
        boolean[] neg = new boolean[N];
        double[] expected = new double[N];
        for (int i = 0; i < N; i++) {
            long[] v = next(kind, rnd);
            u[i] = v[0];
            n[i] = (int) v[1];
            // 10% negatives
            // neg[i] = rnd.nextInt(10) == 1;
            neg[i] = rnd.nextBoolean();
            // guard: do not measure a wrong implementation
            expected[i] = new BigDecimal(v[0]).add(BigDecimal.valueOf(v[1], 9)).doubleValue();
            if (neg[i]) {
                expected[i] = -expected[i];
            }
        }
        for (int i = 0; i < N; i++) {
            data[i] = neg[i] ? new Decimal9(-u[i], -n[i]) : new Decimal9(u[i], n[i]);

            long expectedBits = Double.doubleToRawLongBits(expected[i]);

            if (Double.doubleToRawLongBits(Decimal9Converter.toDouble(data[i])) != expectedBits) {
                throw new IllegalStateException("Wrong result for units=" + u[i] + " nanos=" + n[i]);
            }
        }
    }

    @Benchmark
    public void toDouble_Decimal9(Blackhole blackhole) {
        for (Decimal9 d : data) {
            blackhole.consume(Decimal9Converter.toDouble(d));
        }
    }


    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(Decimal9Benchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
