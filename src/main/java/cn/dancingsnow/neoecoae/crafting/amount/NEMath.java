package cn.dancingsnow.neoecoae.crafting.amount;

/**
 * Saturating arithmetic for non-negative display/capacity quantities.
 *
 * <p>Negative operands are deliberately treated as zero: callers feed values from third-party energy, storage and
 * transfer APIs, and the Flux Networks demand mixin relies on {@code saturatingAdd(total, request) - total}
 * dropping negative requests. Exact accounting paths must use {@code BigInteger}/{@link PlannerAmount} instead.</p>
 */
public final class NEMath {
    private NEMath() {
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
