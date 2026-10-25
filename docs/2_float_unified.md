Да, можно взять одно значение для обеих функций: **63 бита**. Тогда общий набор констант и один общий код округления.

## Почему 63, а не 46

46 бит — минимум, при котором `q` содержит бит округления. Для `toFloatNanosHi` разницы между 46 и 63 нет: `hi != 0`, поэтому длина W в `[65; 93]`, и при любом `N ≤ 63` сдвиг всегда вправо. Выбирать между ними можно как угодно.

Для `toFloatNanosLo` разница есть. Там `lo` имеет от 25 до 64 бит:

- при N = 46 нужны оба направления: влево для 25…45 бит и вправо для 47…64 бит, причём вправо с `sticky` из многих отброшенных бит;
- при N = 63 влево сдвигается всё, кроме `lo ≥ 2^63`, когда сдвиг вправо ровно на 1 бит. Левый сдвиг ничего не теряет, поэтому `sticky` почти всегда берётся только из остатка деления.

Выше 63 подняться нельзя: `w` должно оставаться положительным `long` (иначе вместо обычного деления нужен `divideUnsigned`). Значит, 63 — это максимум, он подходит обеим функциям, и его стоит взять как общий. Скорость не должна меняться: HotSpot превращает `long / const` в умножение со старшей половиной (mulhi) независимо от величины делимого. Но я это не измерял.

## Общее ядро и константы

С N = 63 различие между hi и lo сводится к подготовке `w`: в обеих функциях значение равно `w · 2^shift · 10^-9`, где `shift` — число бит, на которое сдвинули вправо (в lo-версии оно может быть отрицательным). Остальное (деление, округление, сборка float) общее.

```java
// ---- общие: 10^9 = 2^9 * 5^9  =>  W * 10^-9 = (W / 5^9) * 2^-9
private static final long D5_9 = 1_953_125L;          // 5^9;  2^20 < D5_9 < 2^21
private static final int POW2_EXP = 9;                // 2^9

// ---- float
private static final int F_PRECISION = 24;                          // бит мантиссы с неявной единицей
private static final int F_MANT_BITS = F_PRECISION - 1;             // 23 хранимых бита
private static final int F_EXP_BIAS = 127;
// value = m * 2^p, m ∈ [2^23; 2^24]:  биты = ((p + 23 + 127) << 23) + m - 2^23 = ((p + 149) << 23) + m
private static final int F_EXP_OFFSET = F_MANT_BITS + F_EXP_BIAS - 1;   // 149

private static final int F_NORM_BITS = 63;                          // w ∈ [2^62; 2^63), максимум для положительного long
// bitLength(floor(2^62 / D)) = 42; q бывает длиной 42 или 43 бита
private static final int F_Q_MIN_BITS =
        64 - Long.numberOfLeadingZeros((1L << (F_NORM_BITS - 1)) / D5_9);   // 42
private static final int F_DROP_MIN = F_Q_MIN_BITS - F_PRECISION;           // 18; drop = 18 или 19
```

Здесь `F_Q_MIN_BITS` не записан числом, а вычисляется. Для `static final` значения JIT всё равно подставит как константу, а при смене `F_NORM_BITS` или `D5_9` инварианты пересчитаются сами. Условие корректности `F_Q_MIN_BITS ≥ F_PRECISION + 1` можно проверить тестом.

```java
/**
 * value = (w + ε) * 2^shift * 10^-9, где w ∈ [2^62; 2^63), ε ∈ [0; 1) задаётся флагом sticky
 * (были отброшены ненулевые младшие биты). Результат нормальный float, round-half-even.
 */
private static float roundFloat(long w, boolean sticky, int shift) {
    long q = w / D5_9;                                    // 42 или 43 бита
    sticky |= w != q * D5_9;                              // остаток деления != 0

    int drop = F_DROP_MIN + (int) (q >>> F_Q_MIN_BITS);   // 18 или 19: q >>> 42 == 1 <=> q 43-битное
    long m = q >>> drop;                                  // 24 бита
    long rem = q & ((1L << drop) - 1);
    long half = 1L << (drop - 1);
    if (rem > half || (rem == half && (sticky || (m & 1) != 0))) m++;

    int p = drop + shift - POW2_EXP;                      // value ≈ m * 2^p
    return Float.intBitsToFloat((int) (((long) (p + F_EXP_OFFSET) << F_MANT_BITS) + m));
}

/** (hi:lo) * 10^-9 -> float, hi != 0 (W ∈ [2^64; 2^93)). */
public static float toFloatNanosHi(long hi, long lo) {
    int sh = (128 - Long.numberOfLeadingZeros(hi)) - F_NORM_BITS;     // 2..30, всегда > 0
    long w = (lo >>> sh) | (hi << (64 - sh));                         // 63 бита, старшие не теряются
    boolean sticky = (lo & ((1L << sh) - 1)) != 0;
    return roundFloat(w, sticky, sh);
}

/** lo (unsigned, lo != 0) * 10^-9 -> float, для hi == 0. */
public static float toFloatNanosLo(long lo) {
    int sh = (64 - Long.numberOfLeadingZeros(lo)) - F_NORM_BITS;      // -62..1
    if (sh > 0) {                                                     // lo >= 2^63: сдвиг на 1 бит вправо
        return roundFloat(lo >>> sh, (lo & ((1L << sh) - 1)) != 0, sh);
    }
    return roundFloat(lo << -sh, false, sh);                          // без потерь
}
```

## Проверка границ

- Hi: `hi` имеет `bits − 64 ≤ 29` бит, после сдвига на `64 − sh` получается `bits − sh = 63` бита. Старшие биты не теряются, а `sh ∈ [2; 30]`, поэтому оба сдвига корректны (`64 − sh ≤ 62`).
- `q ∈ [2^41,1; 2^42,1)`, то есть 42 или 43 бита. `m` после сдвига на `drop` получает ровно 24 бита.
- `p = drop + sh − 9`: для hi `p ∈ [18 + 2 − 9; 19 + 30 − 9] = [11; 40]`, для lo `p ∈ [18 − 62 − 9 + ...]`. Конкретно для `lo > 2^24` (иначе работает Clinger) `sh ≥ −38`, и `p ≥ 18 − 38 − 9 = −29`, поле экспоненты `p + 150 ≥ 121`. Субнормалов и переполнения нет в обоих случаях.
- Перенос `m == 2^24` даёт `+1` к экспоненте и нулевую мантиссу, как и раньше.

## Что обновить в тестах

Инварианты теперь общие для двух функций:

```java
@Test
void floatNormalizationInvariants() {
    BigInteger d = BigInteger.valueOf(1_953_125L);
    BigInteger wMin = BigInteger.ONE.shiftLeft(62);
    BigInteger wMax = BigInteger.ONE.shiftLeft(63).subtract(BigInteger.ONE);
    assertEquals(42, wMin.divide(d).bitLength());          // F_Q_MIN_BITS
    assertEquals(43, wMax.divide(d).bitLength());          // F_Q_MIN_BITS + 1
    assertTrue(42 >= 24 + 1);                              // есть бит округления
}
```

Отдельно тесты на `hi != 0` и `lo`-ветку остаются прежними (`randomByBitLength` с `bits` от 25 и `floatLoStickyFromShift`). В `floatLoStickyFromShift` теперь `drop` равен 18 или 19, как и раньше. Благодаря общему `roundFloat` большая часть логики проверяется сразу двумя путями.

Для double ничего менять не нужно: там и hi-, и lo-версия уже используют одну и ту же нормализацию в 76 бит и один набор констант, а 63 бит не хватит (нужно ≥ 75).