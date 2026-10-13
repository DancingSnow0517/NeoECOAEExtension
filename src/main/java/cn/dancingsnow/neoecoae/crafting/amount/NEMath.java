package cn.dancingsnow.neoecoae.crafting.amount;

import java.math.BigInteger;

public final class NEMath {
    private NEMath() {
    }

    public static long saturatingAdd(long left, long right) {
        left = Math.max(0L, left);
        right = Math.max(0L, right);
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public static int saturatingAddInt(int left, int right) {
        left = Math.max(0, left);
        right = Math.max(0, right);
        return left > Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
    }

    public static long clampToLong(BigInteger value) {
        if (value == null || value.signum() < 0) {
            return 0L;
        }
        return value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : value.longValue();
    }

    public static long saturatingMultiply(long left, long right) {
        left = Math.max(0L, left);
        right = Math.max(0L, right);
        if (left == 0L || right == 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
