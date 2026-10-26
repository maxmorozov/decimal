package io.github.maxmorozov.decimal;

import java.util.Random;

public class PrintAsm {
    public static void main(String[] args) {
        Random rnd = new Random(42);
        FPDecimal[] data = new FPDecimal[4096];
        FPDecimalV3[] data_v3 = new FPDecimalV3[4096];
        FPDecimalV4[] data_v4 = new FPDecimalV4[4096];
        FPDecimalV5[] data_v5 = new FPDecimalV5[4096];
        for (int i = 0; i < data.length; i++) {
            long[] v = FPDecimalFloatBenchmark.next("mixed", rnd);
            data[i] = new FPDecimal(false, v[0], (int) v[1]);
            data_v3[i] = new FPDecimalV3(false, v[0], (int) v[1]);
            data_v4[i] = new FPDecimalV4(false, v[0], (int) v[1]);
            data_v5[i] = new FPDecimalV5(false, v[0], (int) v[1]);
        }
        float sum = 0;
        for (int rep = 0; rep < 500; rep++) {          // ~2 млн вызовов: хватит и для C1, и для C2
            for (FPDecimal d : data) {
                sum += d.toFloat();
            }
            for (FPDecimalV3 d : data_v3) {
                sum += d.toFloat();
            }
            for (FPDecimalV4 d : data_v4) {
                sum += d.toFloat();
            }
            for (FPDecimalV5 d : data_v5) {
                sum += d.toFloat();
            }
        }
        System.out.println(sum);                       // чтобы цикл не вырезали
    }
}