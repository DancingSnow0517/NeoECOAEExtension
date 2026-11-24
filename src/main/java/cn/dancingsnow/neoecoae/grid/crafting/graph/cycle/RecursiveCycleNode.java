package cn.dancingsnow.neoecoae.grid.crafting.graph.cycle;

import cn.dancingsnow.neoecoae.grid.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.grid.crafting.planner.report.snapshot.CraftingGraphSnapshot.RecursionLayer;
import org.jetbrains.annotations.Nullable;

/** One occurrence in a left-to-right recursion preview; the original material snapshot remains unchanged. */
public record RecursiveCycleNode(Role role, int level, PlannerAmount amount, @Nullable RecursionLayer layer,
        @Nullable PlannerAmount delta, @Nullable RecursiveCycleLedger.Balance balance) {
    public RecursiveCycleNode(Role role, int level, PlannerAmount amount, @Nullable RecursionLayer layer) {
        this(role, level, amount, layer, null, null);
    }
    public enum Role { SEED, STOCK, INPUT, STEP, OUTPUT, RETAINED, UNKNOWN }
}
