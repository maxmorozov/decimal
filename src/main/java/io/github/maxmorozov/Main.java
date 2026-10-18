package io.github.maxmorozov;

import io.github.maxmorozov.decimal.Decimal;
import io.github.maxmorozov.decimal.DecimalConverter;
import io.github.maxmorozov.decimal64.Decimal64;
import io.github.maxmorozov.decimal64.Decimal64Converter;
import io.github.maxmorozov.schubfach.Schubfach;
import io.github.maxmorozov.schubfach.Schubfach.DoubleEncoder;

public class Main {

    public static void main(String[] args) {
        DoubleEncoder encoder = (negative, significand, exponent) -> {
            StringBuilder builder = new StringBuilder();
            if (negative) {
                builder.append("-");
            }
            builder.append(significand);
            builder.append("e");
            builder.append(exponent);
            System.out.println(builder);
        };
        Schubfach.encodeDouble(8.1, encoder);

        testDecimal64(Decimal64Converter.encode(8.1));
        testDecimal64(false, 1, 30);

        testDecimal64(false, 9_999_999_999_999_999L, -2);
        testDecimal(false, 9_999_999_999_999_999L, -2);
        testDecimal(false, 9007199254740993L, 0);

        System.out.println("\nPaxon test w*2^q");
        Paxon.testPaxsonDouble(Main::testDecimal);

        System.out.println("\nPaxon test w*10^q");
        Paxon.testPaxsonDecimal(Main::testDecimal);
    }

    private static void testDecimal64(double value) {
        testDecimal64(Decimal64Converter.encode(value));
    }

    private static void testDecimal(double value) {
        testDecimal(DecimalConverter.encode(value));
    }

    private static void testDecimal64(boolean negative, long significand, int exponent) {
        long decimal = Decimal64.encode(negative, significand, exponent);
        testDecimal64(decimal);
    }

    private static void testDecimal64(long decimal) {
        System.out.printf("%X -> %s%dE%d -> %s\n",
                decimal,
                Decimal64.isNegative(decimal) ? "-" : "",
                Decimal64.significand(decimal),
                Decimal64.exponent(decimal),
                Decimal64Converter.toDouble(decimal));
    }

    private static void testDecimal(boolean negative, long significand, int exponent) {
        Decimal decimal = new Decimal(negative, significand, exponent, Schubfach.NON_SPECIAL);
        testDecimal(decimal);
    }

    private static void testDecimal(Decimal decimal) {
        double d = DecimalConverter.toDouble(decimal);
        System.out.printf("%s%dE%d -> %s, range=[%s,%s]\n",
                decimal.negative() ? "-" : "",
                decimal.significand(),
                decimal.exponent(),
                d,
                Math.nextDown(d),
                Math.nextUp(d));
    }
}