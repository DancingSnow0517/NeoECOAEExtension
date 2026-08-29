package cn.dancingsnow.neoecoae.impl.crafting.planner.bridge;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.SolveState;
import java.util.Map;

public final class AE2CraftingPlanBridge {
    public CraftingPlan success(AEKey goal, long amount, boolean simulation, boolean multiplePaths, SolveState state) {
        return new CraftingPlan(new GenericStack(goal, amount), Math.max(0, state.bytes()), simulation, multiplePaths,
            state.usedItems(), state.emittedItems(), state.missingItems(), state.patternTimes());
    }

    public CraftingPlan unsupported(AEKey goal, long amount) {
        KeyCounter missing = new KeyCounter();
        missing.add(goal, amount);
        return new CraftingPlan(new GenericStack(goal, amount), 0, true, false,
            new KeyCounter(), new KeyCounter(), missing, Map.of());
    }
}
