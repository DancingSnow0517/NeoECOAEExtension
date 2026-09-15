package cn.dancingsnow.neoecoae.util;

public final class NEMath {
    private NEMath() {}

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static long saturatingAdd(long left, long right) {
        left = Math.max(0L, left);
        right = Math.max(0L, right);
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
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
