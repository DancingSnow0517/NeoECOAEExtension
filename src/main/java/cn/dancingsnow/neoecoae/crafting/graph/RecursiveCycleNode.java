package cn.dancingsnow.neoecoae.crafting.graph;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.RecursionLayer;
import org.jetbrains.annotations.Nullable;

/** One occurrence in a bottom-up recursion preview; the original material snapshot remains unchanged. */
public record RecursiveCycleNode(Role role, int level, PlannerAmount amount, @Nullable RecursionLayer layer,
        @Nullable PlannerAmount delta, @Nullable RecursiveCycleLedger.Balance balance) {
    public RecursiveCycleNode(Role role, int level, PlannerAmount amount, @Nullable RecursionLayer layer) {
        this(role, level, amount, layer, null, null);
    }
    public enum Role { SEED, STOCK, INPUT, STEP, DELIVERY, OUTPUT, RETAINED, UNKNOWN }
}
