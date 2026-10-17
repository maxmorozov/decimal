package io.github.maxmorozov.decimal;

public record Decimal(boolean negative, long significand, int exponent, int type) {

}
