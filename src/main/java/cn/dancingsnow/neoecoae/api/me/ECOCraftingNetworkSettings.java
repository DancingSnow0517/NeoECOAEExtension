package cn.dancingsnow.neoecoae.api.me;

import appeng.api.networking.IGrid;
import org.jetbrains.annotations.Nullable;

/**
 * ECO crafting-planning settings owned by one AE grid's crafting service.
 */
public interface ECOCraftingNetworkSettings {
    boolean neoecoae$isIgnoringPatternSubstitutions();

    void neoecoae$setIgnoringPatternSubstitutions(boolean ignoringPatternSubstitutions);

    int neoecoae$getSubstitutionPatternCount();

    boolean neoecoae$isFastPlannerEnabled();

    void neoecoae$setFastPlannerEnabled(boolean enabled);

    /** Existing planner button controls whether cyclic components are offered to CycleSolver. */
    default boolean neoecoae$isCyclePlanningEnabled() {
        return neoecoae$isFastPlannerEnabled();
    }

    boolean neoecoae$hasComputationHost();

    default boolean neoecoae$shouldUseFastPlanner() {
        return neoecoae$isFastPlannerEnabled() && neoecoae$hasComputationHost();
    }

    static @Nullable ECOCraftingNetworkSettings of(@Nullable IGrid grid) {
        if (grid != null && grid.getCraftingService() instanceof ECOCraftingNetworkSettings settings) {
            return settings;
        }
        return null;
    }
}
