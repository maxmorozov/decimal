package io.github.maxmorozov.decimal;

import io.github.maxmorozov.decimal9.Decimal9;
import io.github.maxmorozov.decimal9.Decimal9Converter;
import io.github.maxmorozov.fpdecimal.FPDecimal;
import io.github.maxmorozov.fpdecimal.FPDecimalConverter;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.math.BigInteger;
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
@OperationsPerInvocation(FPDecimalDoubleBenchmark.N)
public class FPDecimalDoubleBenchmark {

    static final int N = 262144;

    private static final BigInteger E9 = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger D = BigInteger.valueOf(1_953_125L);          // 5^9
    private static final BigInteger MAX_W = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(E9).add(BigInteger.valueOf(999_999_999L));

    @Param({"clinger", "lo", "hi_left", "hi_right", "hi_both", "integers", "ties", "mixed", "bank"})
    private String kind;

    private FPDecimal[] data;

    @Setup
    public void setup() {
        Random rnd = new Random(42);
        data = new FPDecimal[N];

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
            neg[i] = isNegative(kind, rnd);
            // guard: do not measure a wrong implementation
            expected[i] = new BigDecimal(v[0]).add(BigDecimal.valueOf(v[1], 9)).doubleValue();
            if (neg[i]) {
                expected[i] = -expected[i];
            }
        }
        for (int i = 0; i < N; i++) {
            data[i] = new FPDecimal(neg[i], u[i], n[i]);

            long expectedBits = Double.doubleToRawLongBits(expected[i]);
            if (Double.doubleToRawLongBits(FPDecimalConverter.toDoubleV1(data[i])) != expectedBits) {
                throw new IllegalStateException("Wrong result v1 for units=" + u[i] + " nanos=" + n[i]);
            }
            if (Double.doubleToRawLongBits(FPDecimalConverter.toDoubleV3(data[i])) != expectedBits) {
                throw new IllegalStateException("Wrong result v3 for units=" + u[i] + " nanos=" + n[i]);
            }
            if (Double.doubleToRawLongBits(FPDecimalConverter.toDoubleV4(data[i])) != expectedBits) {
                throw new IllegalStateException("Wrong result v4 for units=" + u[i] + " nanos=" + n[i]);
            }
            if (Double.doubleToRawLongBits(FPDecimalConverter.toDoubleV5(data[i])) != expectedBits) {
                throw new IllegalStateException("Wrong result v5 for units=" + u[i] + " nanos=" + n[i]);
            }
            if (Double.doubleToRawLongBits(FPDecimalConverter.toDoubleV6(data[i])) != expectedBits) {
                throw new IllegalStateException("Wrong result v6 for units=" + u[i] + " nanos=" + n[i]);
            }
        }
    }

    @Benchmark
    public void toDouble_V1(Blackhole blackhole) {
        for (FPDecimal d : data) {
            blackhole.consume(FPDecimalConverter.toDoubleV1(d));
        }
    }

    @Benchmark
    public void toDouble_V3(Blackhole blackhole) {
        for (FPDecimal d : data) {
            blackhole.consume(FPDecimalConverter.toDoubleV3(d));
        }
    }

    @Benchmark
    public void toDouble_V4(Blackhole blackhole) {
        for (FPDecimal d : data) {
            blackhole.consume(FPDecimalConverter.toDoubleV4(d));
        }
    }

    @Benchmark
    public void toDouble_V5(Blackhole blackhole) {
        for (FPDecimal d : data) {
            blackhole.consume(FPDecimalConverter.toDoubleV5(d));
        }
    }

    @Benchmark
    public void toDouble_V6(Blackhole blackhole) {
        for (FPDecimal d : data) {
            blackhole.consume(FPDecimalConverter.toDoubleV6(d));
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(FPDecimalDoubleBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
