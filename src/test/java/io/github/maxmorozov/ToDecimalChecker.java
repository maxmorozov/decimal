/*
 * Copyright 2018-2020 Raffaello Giulietti
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.github.maxmorozov;

import java.math.BigDecimal;
import java.math.BigInteger;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.TEN;

/*
A checker for the Javadoc specification.
It just relies on straightforward use of (expensive) BigDecimal arithmetic,
not optimized at all.
 */
abstract class ToDecimalChecker extends BasicChecker {

    // The string to check
    private final double s;

    // The decimal parsed from s is c 10^q
    private long c;
    private int q;

    ToDecimalChecker(double s) {
        this.s = s;
    }

    /*
    Returns e be such that 10^(e-1) <= v < 10^e.
     */
    static int e(double v) {
        // log10(v) + 1 is a first good approximation of e
        int e = (int) Math.floor(Math.log10(v)) + 1;

        // Full precision search for e such that 10^(e-1) <= c 2^q < 10^e.
        BigDecimal vp = new BigDecimal(v);
        BigDecimal low = new BigDecimal(BigInteger.ONE, -(e - 1));
        while (low.compareTo(vp) > 0) {
            e -= 1;
            low = new BigDecimal(BigInteger.ONE, -(e - 1));
        }
        BigDecimal high = new BigDecimal(BigInteger.ONE, -e);
        while (vp.compareTo(high) >= 0) {
            e += 1;
            high = new BigDecimal(BigInteger.ONE, -e);
        }
        return e;
    }

    static long cTiny(int qMin, int kMin) {
        BigInteger[] qr = ONE.shiftLeft(-qMin)
                .divideAndRemainder(TEN.pow(-(kMin + 1)));
        BigInteger cTiny = qr[1].signum() > 0 ? qr[0].add(ONE) : qr[0];
        assertTrue(cTiny.bitLength() < Long.SIZE, "C_TINY");
        return cTiny.longValue();
    }

    void assertTrue() {
        if (isOK()) {
            return;
        }
        String msg = "toString applied to the bits " +
                hexBits() +
                " returns " +
                "\"" + s + "\"" +
                ", which is not correct according to the specification.";
        if (FAILURE_THROWS_EXCEPTION) {
            throw new RuntimeException(msg);
        }
        System.err.println(msg);
    }

    private boolean isOK() {
        if (isNaN()) {
            return Double.isNaN(s);
        }
        double t = s;
        if (isNegative()) {
            if (Double.isNaN(s) || s > 0) {
                return false;
            }
            negate();
            t = -s;
        }
        if (isInfinity()) {
            return Double.isInfinite(t);
        }
        if (isZero()) {
            return t == 0.0;
        }

        // s must recover v
        return recovers(t);
    }

    abstract BigDecimal toBigDecimal();

    abstract boolean recovers(BigDecimal b);

    abstract boolean recovers(double b);

    abstract String hexBits();

    abstract int minExp();

    abstract int maxExp();

    abstract int maxLen10();

    abstract boolean isZero();

    abstract boolean isInfinity();

    abstract void negate();

    abstract boolean isNegative();

    abstract boolean isNaN();

}
