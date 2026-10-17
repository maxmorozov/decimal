package io.github.maxmorozov.decimal64;

import io.github.maxmorozov.schubfach.Schubfach;

public class Decimal64Converter {

    public static final long PLUS_ZERO = 0x31C0000000000000L;
    public static final long MINUS_ZERO = PLUS_ZERO | (1L << 63); // 0xB1C0000000000000
    // Combination field = 0b11110 (shifted by 57)
    public static final long PLUS_INF = 0b11110L << 58; // 0x7800000000000000L
    // Sign bit = 1, Combination field = 0b11110
    public static final long MINUS_INF = (1L << 63) | PLUS_INF; // 0xF800000000000000L
    // Combination field = 0b11111, J-bit (bit 56) = 0
    public static final long Q_NAN = (0b11111L << 58); // 0x7C00000000000000L
    // Combination field = 0b11111, J-bit (bit 56) = 1
    public static final long S_NAN = Q_NAN | (1L << 57);  // 0x7E00000000000000L


    public static long encode(double v) {
        Decimal64Encoder encoder = new Decimal64Encoder();

        int type = Schubfach.encodeDouble(v, encoder);
        return switch (type) {
            case Schubfach.NON_SPECIAL -> encoder.result();
            case Schubfach.PLUS_ZERO -> PLUS_ZERO;
            case Schubfach.MINUS_ZERO -> MINUS_ZERO;
            case Schubfach.PLUS_INF -> PLUS_INF;
            case Schubfach.MINUS_INF -> MINUS_INF;
            case Schubfach.Q_NAN -> Q_NAN;
            case Schubfach.S_NAN -> S_NAN;
            default -> throw new IllegalStateException("Unexpected type value: " + type);
        };
    }

    /**
     * Converts an IEEE 754 decimal64 (BID) bit pattern into a standard Java double.
     *
     * @param bits The 64-bit long containing packed decimal64 bits
     * @return The corresponding double value
     */
    public static double toDouble(long bits) {
        return Decimal64.toDouble(bits);
    }
}
