Частично. Это даёт небольшое упрощение для float и почти ничего для double. Выигрыш по скорости неочевиден, и его надо мерить.

## Что даёт `divideUnsigned`

Главное: делимое может быть беззнаковым 64-битным, то есть `N = 64` вместо 63. Для float это убирает последнюю ветку в `toFloatNanosLo`. При `N = 64` сдвиг `lo` всегда влево на `numberOfLeadingZeros(lo) ≥ 0`, и сдвигов вправо (с `sticky` от потерянного бита) больше нет.

Для `hi != 0` ничего не меняется: сдвиг и так всегда вправо, просто на один бит меньше (`sh ∈ [1; 29]`).

Константы пересчитываются так (`w ∈ [2^63; 2^64)`, `D ≈ 2^20,9`):

- `floor(2^63 / D)` имеет 43 бита, `floor((2^64 − 1) / D)` — 44 бита, значит `q` имеет 43 или 44 бита;
- `drop = 19` или `20`;
- бит округления есть, потому что `43 ≥ 24 + 1`.

```java
private static final int F_NORM_BITS = 64;                       // w ∈ [2^63; 2^64) как unsigned
// bitLength(floor(2^63 / D)) = 43; q бывает длиной 43 или 44 бита
private static final int F_Q_MIN_BITS =
        64 - Long.numberOfLeadingZeros(Long.divideUnsigned(Long.MIN_VALUE, D5_9));
private static final int F_DROP_MIN = F_Q_MIN_BITS - F_PRECISION;   // 19; drop = 19 или 20

private static float roundFloat(long w, boolean sticky, int shift) {   // w — unsigned
    long q = Long.divideUnsigned(w, D5_9);                // 43 или 44 бита, q > 0 как signed
    sticky |= w != q * D5_9;                              // по модулю 2^64 это точный остаток

    int drop = F_DROP_MIN + (int) (q >>> F_Q_MIN_BITS);   // q >>> 43 == 1 <=> q 44-битное
    long m = q >>> drop;
    long rem = q & ((1L << drop) - 1);
    long half = 1L << (drop - 1);
    if (rem > half || (rem == half && (sticky || (m & 1) != 0))) m++;

    int p = drop + shift - POW2_EXP;
    return Float.intBitsToFloat((int) (((long) (p + F_EXP_OFFSET) << F_MANT_BITS) + m));
}

public static float toFloatNanosHi(long hi, long lo) {
    int sh = (128 - Long.numberOfLeadingZeros(hi)) - F_NORM_BITS;     // 1..29
    long w = (lo >>> sh) | (hi << (64 - sh));
    boolean sticky = (lo & ((1L << sh) - 1)) != 0;
    return roundFloat(w, sticky, sh);
}

public static float toFloatNanosLo(long lo) {                        // lo != 0
    int s = Long.numberOfLeadingZeros(lo);                           // 0..63
    return roundFloat(lo << s, false, -s);                           // без ветвлений, без потерь
}
```

Проверка диапазонов: для `lo > 2^24` имеем `s ≤ 39`, и `p = drop − s − 9 ≥ 19 − 39 − 9 = −29`. Поле экспоненты `p + 150 ≥ 121`, субнормалов нет. Для hi: `p ≤ 20 + 29 − 9 = 40`, поле `≤ 190 < 255`, переполнения нет.

## Что надо учесть

- **Версия JDK.** Интринсик появился в JDK 19 (сначала x86_64, на AArch64 позже). До этого `divideUnsigned` — обычный Java-код, который при отрицательном делимом делает несколько дополнительных операций, поэтому выигрыш пропадает, а при отрицательном `w` код может даже замедлиться. Версию вашего JDK я не знаю.
- **Деление на константу.** Для знакового `long / const` C2 гарантированно строит умножение вместо `idiv`. Заменяет ли он так же беззнаковое деление на константу, я точно не помню. Если нет, `divideUnsigned` превратится в настоящую инструкцию `div`, и это будет заметно медленнее текущей версии с 63 битами. Поэтому мерить обязательно. При проигрыше оставьте `N = 63`, потеря там одна ветка и небольшая.
- **Корректность `w != q * D5_9`.** Работает и для беззнаковых: `q·D ≤ w`, а разность по модулю 2^64 совпадает с настоящим остатком, поэтому достаточно сравнить на равенство.

## Double

Для double это не помогает. Для `q` нужно минимум 54 бита, то есть делимое ≥ 75 бит, и оно по-прежнему не помещается в `long`. Два деления по 32 бита остаются. Можно теоретически использовать куски по 64 бита, но тогда остаётся то же число шагов, а `divideUnsigned` ничего не сокращает. Поэтому `toDoubleNanosHi` и `toDoubleNanosLo` я бы не менял.

## Тесты

- Все существующие float-тесты (`floatTies`, `roundingCarry`, `randomByBitLength` с `bits` от 25) работают без изменений.
- `floatLoStickyFromShift` теряет смысл (сдвигов вправо в lo-версии больше нет). Вместо него добавьте проверку на `lo ≥ 2^63` без `sticky` из сдвига, которая и так покрывается `loBoundaries`.
- Инварианты обновите так:

```java
assertEquals(43, pow2(63).divide(d).bitLength());
assertEquals(44, pow2(64).subtract(BigInteger.ONE).divide(d).bitLength());
```

Порядок действий: сначала бенчмарк на вашем JDK, потом решение. Версия с 64 битами чуть короче по коду, а с 63 — стабильнее по скорости на любом JDK.