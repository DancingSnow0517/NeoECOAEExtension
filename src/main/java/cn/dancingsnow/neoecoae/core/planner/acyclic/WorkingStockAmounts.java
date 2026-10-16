package cn.dancingsnow.neoecoae.core.planner.acyclic;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;

/** Working-stock quantities after the platform has decoded a tool's durability contract. */
public final class WorkingStockAmounts {
    private WorkingStockAmounts() {}

    public static PlannerAmount requiredTools(PlannerAmount uses, int capacityPerTool) {
        if (capacityPerTool <= 0) throw new IllegalArgumentException("capacityPerTool must be positive");
        return uses.ceilDiv(PlannerAmount.of(capacityPerTool));
    }

    /** The use reaching maxDamage still succeeds; only the next use needs a replacement tool. */
    public static int durabilityUsesBeforeBreak(int damage, int damagePerUse, int maxDamage) {
        if (damage < 0 || damagePerUse <= 0 || maxDamage <= damage) return 0;
        long remaining = (long) maxDamage - damage;
        return Math.toIntExact((remaining + damagePerUse - 1L) / damagePerUse);
    }
}
