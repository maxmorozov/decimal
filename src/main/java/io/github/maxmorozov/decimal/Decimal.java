package io.github.maxmorozov.decimal;

import io.github.maxmorozov.schubfach.Schubfach.EncodingResult;

public record Decimal(boolean negative, long significand, int exponent, EncodingResult type) {

}
