package com.spot.common.money;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class Atomic {
    public static final int DEFAULT_DECIMALS = 8;
    public static final BigInteger TEN = BigInteger.TEN;

    private Atomic() {
    }

    public static long parse(String s, int decimals) {
        try {
            BigDecimal bd = new BigDecimal(s.trim());
            if (bd.signum() < 0) {
                throw new IllegalArgumentException("NEGATIVE");
            }
            bd = bd.setScale(decimals, RoundingMode.DOWN);
            BigDecimal scaled = bd.movePointRight(decimals);
            return scaled.longValueExact();
        } catch (Exception e) {
            throw new IllegalArgumentException("INVALID_NUMBER");
        }
    }

    public static String format(long atomic, int decimals) {
        BigDecimal bd = BigDecimal.valueOf(atomic, decimals);
        return bd.stripTrailingZeros().toPlainString();
    }

    public static long mulDivDown(long a, long b, long denom) {
        BigInteger bi = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
        return bi.divide(BigInteger.valueOf(denom)).longValueExact();
    }

    public static long quoteQtyFromPriceQty(long priceAtomic, long qtyAtomic) {
        return mulDivDown(priceAtomic, qtyAtomic, scale(DEFAULT_DECIMALS));
    }

    public static long scale(int decimals) {
        return TEN.pow(decimals).longValueExact();
    }
}
