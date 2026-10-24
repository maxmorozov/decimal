Да, и версия «только `lo`» получается проще, чем общая: в ней нет ветки по знаку `sh`. Для float она ещё и использует одно деление. Для double выигрыш будет скромнее, об этом ниже. Код я не запускал.

## Float, lo-only: нормализация до 63 бит

При `hi == 0` можно нормализовать `lo` не до `precision + 22` бит, а до 63. Тогда `w ∈ [2^62; 2^63)` умещается в положительный `long`, и деление остаётся одно. Сдвиг влево ничего не теряет. Вправо приходится сдвигать только при `lo ≥ 2^63`, и то на один бит.

Длина частного: `2^62 / D ≈ 2^41,1` и `2^63 / D ≈ 2^42,1`, то есть `q` имеет 42 или 43 бита. Бит округления и остальные биты мантиссы у float (24) заведомо есть. Поэтому `drop = len(q) − 24 = 18 + (q >>> 42)`.

```java
/** lo (unsigned, lo != 0) * 10^-9 -> float, round-half-even. Для hi == 0. */
public static float toFloatNanosLo(long lo) {
    int s = Long.numberOfLeadingZeros(lo) - 1;           // -1..62; w = lo * 2^s ∈ [2^62; 2^63)
    long w;
    boolean sticky;
    if (s >= 0) {
        w = lo << s;
        sticky = false;
    } else {                                             // lo >= 2^63: сдвиг на 1 вправо
        w = lo >>> 1;
        sticky = (lo & 1) != 0;
    }

    long q = w / D5_9;                                   // [2^41; 2^43): 42 или 43 бита
    sticky |= w != q * D5_9;                             // остаток деления != 0

    int drop = 18 + (int) (q >>> 42);                    // len(q) - 24 = 18 или 19
    long m = q >>> drop;                                 // 24 бита
    long rem = q & ((1L << drop) - 1);
    long half = 1L << (drop - 1);
    if (rem > half || (rem == half && (sticky || (m & 1) != 0))) m++;

    int p = drop - s - 9;                                // value ≈ m * 2^p
    return Float.intBitsToFloat((int) (((long) (p + 149) << 23) + m));
}
```

Проверка границ:
- `value = W·10^-9 = (w·2^-s / D)·2^-9`, откуда `p = drop − s − 9`.
- Для `lo ≥ 1` значение не меньше 10^-9, то есть оно нормальное. Для `lo ≤ 2^64` оно не больше `1,8·10^10`, переполнения нет. Поле экспоненты `p + 150` для `lo > 2^24` лежит в `[121; 161]`.
- Условие `lo > 2^24` здесь для корректности не нужно. Нормализация работает для любого `lo ≠ 0`. Порог остаётся только как быстрый путь Clinger.

## Double, lo-only: только сдвиг влево

Для double нормализации до 63 бит не хватит: нужно `q` минимум в 54 бита, то есть `W'` минимум в 75 бит. Поэтому остаются два деления по 32 бита, но ветки пропадают. При `lo > 2^53` длина `lo` лежит в `[54; 64]`, и всегда `k = 76 − bits = 12 + clz ∈ [12; 22]`. Потерь нет, `sticky` берётся только из остатка, и граничный случай `k = 0` не возникает.

```java
/** lo (unsigned, lo > 2^53) * 10^-9 -> double, round-half-even. Для hi == 0. */
public static double toDoubleNanosLo(long lo) {
    int k = 12 + Long.numberOfLeadingZeros(lo);          // 76 - bits, 12..22
    long h = lo >>> (64 - k);                            // 12 бит: h < 2^12
    long l = lo << k;

    long cur = (h << 32) | (l >>> 32);                   // < 2^44
    long q1 = cur / D5_9;
    long r = cur - q1 * D5_9;
    cur = (r << 32) | (l & 0xFFFFFFFFL);                 // < D * 2^32 < 2^53
    long q0 = cur / D5_9;
    r = cur - q0 * D5_9;
    long q = (q1 << 32) | q0;                            // 55 или 56 бит

    int drop = 2 + (int) (q >>> 55);
    long m = q >>> drop;
    long rem = q & ((1L << drop) - 1);
    long half = 1L << (drop - 1);
    if (rem > half || (rem == half && (r != 0 || (m & 1) != 0))) m++;

    int p = drop - k - 9;                                // [-29; -18]
    return Double.longBitsToDouble(((long) (p + 1074) << 52) + m);
}
```

Здесь я бы не обещал выигрыша перед Eisel-Lemire. Раньше `roundNanos` без таких упрощений давал 5,8 нс против 4,5 нс на одном `compute_float`. Два зависимых деления остались, а убрались только ветки. Выбирайте по бенчмарку: если `toDoubleNanosLo` не обгоняет `compute_float(lo, -9)`, оставьте `compute_float`.

## Использование в FPDecimal

```java
// toDouble()
if (hi == 0) {
    if (Long.compareUnsigned(lo, FAST_MAX_SIG_D) <= 0) {
        v = (double) lo / 1e9;
    } else {
        v = BigMath.toDoubleNanosLo(lo);                 // или compute_float(lo, -9), по бенчмарку
    }
} else {
    v = BigMath.toDoubleNanosHi(hi, lo);
}

// toFloat()
if (hi == 0) {
    if (Long.compareUnsigned(lo, FAST_MAX_SIG_F) <= 0) {
        v = (float) lo / 1e9f;
    } else {
        v = BigMath.toFloatNanosLo(lo);
    }
} else {
    v = BigMath.toFloatNanosHi(hi, lo);
}
```

Hi-версии при этом не меняются, они остаются без лишних веток.

## Тесты

Старый `randomByBitLength` начинался с 54 бит, а для float lo-only нужны длины от 25. Поменяйте начало цикла:

```java
for (int bits = 25; bits <= 93; bits++) {
```

Кроме того, нужен случай, где решение о округлении принимает только бит, потерянный при сдвиге `lo >>> 1` (для `lo ≥ 2^63`). Остаток от деления там нулевой, и без `sticky` из младшего бита результат был бы ошибочным ничьей:

```java
/** lo >= 2^63: потерянный при сдвиге на 1 бит превращает ничью в "чуть выше". */
@Test
void floatLoStickyFromShift() {
    Random rnd = new Random(SEED + 4);
    for (int drop = 18; drop <= 19; drop++) {
        for (long m : mantissas(rnd, 24)) {
            BigInteger q = BigInteger.valueOf(m).shiftLeft(drop).or(pow2(drop - 1));
            BigInteger w = q.multiply(D);                       // W' = 5^9 * q, 2^62 <= W' < 2^63
            if (w.bitLength() != 63) continue;
            check(w.shiftLeft(1).or(BigInteger.ONE));           // W = 2W' + 1, 64 бита, lo < 0
            check(w.shiftLeft(1));                              // настоящая ничья
            check(w.shiftLeft(1).subtract(BigInteger.ONE));     // чуть ниже
        }
    }
}

@Test
void loBoundaries() {
    for (BigInteger w : new BigInteger[]{pow2(63).subtract(BigInteger.ONE), pow2(63),
            pow2(63).add(BigInteger.ONE), pow2(62), pow2(62).subtract(BigInteger.ONE),
            pow2(64).subtract(BigInteger.ONE)}) {
        check(w);
    }
}
```

Инварианты констант для общего теста:

```java
BigInteger d = BigInteger.valueOf(1_953_125L);
assertEquals(42, pow2(62).divide(d).bitLength());               // float lo: q = 42 бита ...
assertEquals(43, pow2(63).subtract(BigInteger.ONE).divide(d).bitLength());   // ... или 43
```