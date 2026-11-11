package io.github.maxmorozov.decimal;

import io.github.maxmorozov.fpdecimal.FPDecimal;
import io.github.maxmorozov.fpdecimal.FPDecimalConverterFloat;

import java.util.Random;

public class PrintAsmFloat {
    public static void main(String[] args) {
        Random rnd = new Random(42);
        FPDecimal[] data = new FPDecimal[4096];
        for (int i = 0; i < data.length; i++) {
            long[] v = FPDecimalFloatBenchmark.next("mixed", rnd);
            data[i] = new FPDecimal(false, v[0], (int) v[1]);
        }
        float sum = 0;
        for (int rep = 0; rep < 500; rep++) {          // ~2 млн вызовов: хватит и для C1, и для C2
            for (FPDecimal d : data) {
                sum += FPDecimalConverterFloat.toFloatV1(d);
            }
            for (FPDecimal d : data) {
                sum += FPDecimalConverterFloat.toFloatV3(d);
            }
            for (FPDecimal d : data) {
                sum += FPDecimalConverterFloat.toFloatV4(d);
            }
            for (FPDecimal d : data) {
                sum += FPDecimalConverterFloat.toFloatV5(d);
            }
            for (FPDecimal d : data) {
                sum += FPDecimalConverterFloat.toFloatV6(d);
            }
        }
        System.out.println(sum);                       // чтобы цикл не вырезали
    }
}