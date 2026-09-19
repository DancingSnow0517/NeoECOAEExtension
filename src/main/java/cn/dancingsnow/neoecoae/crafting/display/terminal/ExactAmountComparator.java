package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.config.SortDir;
import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Map;

/** Compares stored amounts in AE2 display units without truncating or rounding large quantities. */
public final class ExactAmountComparator {
    private ExactAmountComparator() {}

    public static Comparator<GridInventoryEntry> create(Map<AEKey, BigInteger> amounts, SortDir direction) {
        Comparator<GridInventoryEntry> ascending = (left, right) -> compare(left, right, amounts);
        return direction == SortDir.ASCENDING ? ascending : ascending.reversed();
    }

    private static int compare(GridInventoryEntry left, GridInventoryEntry right, Map<AEKey, BigInteger> amounts) {
        BigInteger leftExact = amounts.get(left.getWhat());
        BigInteger rightExact = amounts.get(right.getWhat());
        int leftUnit = left.getWhat().getAmountPerUnit();
        int rightUnit = right.getWhat().getAmountPerUnit();

        if (leftUnit == rightUnit) {
            if (leftExact == null && rightExact == null) {
                return Long.compare(left.getStoredAmount(), right.getStoredAmount());
            }
            return amount(left, leftExact).compareTo(amount(right, rightExact));
        }

        // Cross multiplication preserves fractional fluid units and cannot overflow a long or double.
        return amount(left, leftExact)
                .multiply(BigInteger.valueOf(rightUnit))
                .compareTo(amount(right, rightExact).multiply(BigInteger.valueOf(leftUnit)));
    }

    private static BigInteger amount(GridInventoryEntry entry, BigInteger exact) {
        return exact != null ? exact : BigInteger.valueOf(entry.getStoredAmount());
    }
}
