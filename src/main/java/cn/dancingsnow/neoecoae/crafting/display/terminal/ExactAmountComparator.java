package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.menu.me.common.GridInventoryEntry;
import java.math.BigInteger;
import java.util.Comparator;

/** Compares displayed units without narrowing exact counts to long or double. */
public final class ExactAmountComparator {
    private ExactAmountComparator() {}

    public static Comparator<GridInventoryEntry> ascending(int containerId) {
        return (left, right) -> {
            var leftExact = ExactAmountClientCache.get(containerId, left.getWhat());
            var rightExact = ExactAmountClientCache.get(containerId, right.getWhat());
            boolean leftInfinite = leftExact != null && leftExact.infinite();
            boolean rightInfinite = rightExact != null && rightExact.infinite();
            if (leftInfinite || rightInfinite) {
                return Boolean.compare(leftInfinite, rightInfinite);
            }
            BigInteger leftAmount = leftExact == null
                ? BigInteger.valueOf(left.getStoredAmount()) : leftExact.value();
            BigInteger rightAmount = rightExact == null
                ? BigInteger.valueOf(right.getStoredAmount()) : rightExact.value();
            // Cross-multiplication preserves fractional units (e.g. fluid buckets).
            return leftAmount.multiply(BigInteger.valueOf(right.getWhat().getAmountPerUnit()))
                .compareTo(rightAmount.multiply(BigInteger.valueOf(left.getWhat().getAmountPerUnit())));
        };
    }
}
