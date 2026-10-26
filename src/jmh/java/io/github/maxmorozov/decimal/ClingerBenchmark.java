package io.github.maxmorozov.decimal;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(3)
@State(Scope.Thread)
@OperationsPerInvocation(ClingerBenchmark.N)
public class ClingerBenchmark {
    static final int N = 262144;

    private static final BigInteger E9 = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger MAX_W = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(E9).add(BigInteger.valueOf(999_999_999L));

    private long[] lo;

    @Setup
    public void setup() {
        Random rnd = new Random(42);

        lo = new long[N];

        for (int i = 0; i < N; i++) {
            lo[i] = randomLong(rnd, 1, 53, 0.0);
        }
    }

    private static long randomLong(Random rnd, int minBits, int maxBits, double integerShare) {
        while (true) {
            int bits = minBits + rnd.nextInt(maxBits - minBits + 1);
            BigInteger w = new BigInteger(bits - 1, rnd).setBit(bits - 1);
            if (rnd.nextDouble() < integerShare) {
                w = w.divide(E9).multiply(E9);
            }
            if (w.signum() == 0 || w.compareTo(MAX_W) > 0) {
                continue;
            }
            return w.longValueExact();
        }
    }

    @Benchmark
    public void cast(Blackhole bh) {
        for (long v : lo) bh.consume((double) v / 1e9);
    }

    @Benchmark
    public void magic(Blackhole bh) {
        for (long v : lo)
            bh.consume((Double.longBitsToDouble(0x4330000000000000L | v) - 0x1.0p52) / 1e9);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(ClingerBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
