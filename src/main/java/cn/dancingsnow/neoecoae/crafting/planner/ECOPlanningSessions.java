package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;

/** Captures one inventory snapshot per request, before dispatching any background work. */
public final class ECOPlanningSessions {
    private static final ECOCraftingPlannerService PLANNER = new ECOCraftingPlannerService();
    private ECOPlanningSessions() {}
    public static ECOCraftingPlannerService.Session open(IGrid grid, AEKey goal, ECOPlannerOptions options) {
        var inventory = ECOPlannerInventory.capture(grid);
        return PLANNER.createSession(grid.getCraftingService(), goal, inventory,
            options.cyclePlanningEnabled(), options.ignorePatternSubstitutions(), options.fuzzyPlanningItemIds());
    }
}
