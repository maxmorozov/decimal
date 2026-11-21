package io.github.maxmorozov.decimal;

import io.github.maxmorozov.decimal9.Decimal9;
import io.github.maxmorozov.decimal9.Decimal9Converter;
import io.github.maxmorozov.fpdecimal.FPDecimal;
import io.github.maxmorozov.fpdecimal.FPDecimalConverter;

import java.util.Random;

public class PrintAsmDouble {

    public static final int N = 4096;

    public static void main(String[] args) {
        Random rnd = new Random(42);
        FPDecimal[] data = new FPDecimal[N];
        Decimal9[] data9 = new Decimal9[N];
        String kind = "mixed";
        for (int i = 0; i < data9.length; i++) {
            long[] v = FPDecimalDoubleBenchmark.next(kind, rnd);
            boolean neg = kind.equals("mixed") && rnd.nextBoolean();
            data[i] = new FPDecimal(neg, v[0], (int) v[1]);
            data9[i] = neg ? new Decimal9(-v[0], -(int) v[1]) : new Decimal9(v[0], (int) v[1]);
        }
        double sum = 0;
        for (int rep = 0; rep < 500; rep++) {          // ~2 млн вызовов: хватит и для C1, и для C2
            for (FPDecimal d : data) {
                sum += FPDecimalConverter.toDoubleV1(d);
            }
            for (FPDecimal d : data) {
                sum += FPDecimalConverter.toDoubleV3(d);
            }
            for (FPDecimal d : data) {
                sum += FPDecimalConverter.toDoubleV4(d);
            }
            for (FPDecimal d : data) {
                sum += FPDecimalConverter.toDoubleV5(d);
            }
            for (FPDecimal d : data) {
                sum += FPDecimalConverter.toDoubleV6(d);
            }
            for (Decimal9 d : data9) {
                sum += Decimal9Converter.toDouble(d);
            }
        }
        System.out.println(sum);                       // чтобы цикл не вырезали
    }
}