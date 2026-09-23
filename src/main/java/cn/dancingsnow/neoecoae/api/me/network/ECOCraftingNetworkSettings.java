package cn.dancingsnow.neoecoae.api.me.network;

import appeng.api.networking.IGrid;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
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

    /**
     * Whether an unavoidable cyclic component may be offered to the cycle solver.
     */
    boolean neoecoae$isCyclePlanningEnabled();

    void neoecoae$setCyclePlanningEnabled(boolean enabled);

    /** @deprecated Logging is controlled by the global Calculating debug configuration now. */
    @Deprecated
    default boolean neoecoae$isPlanningLogEnabled() {
        return false;
    }

    /** @deprecated Logging is controlled by the global Calculating debug configuration now. */
    @Deprecated
    default void neoecoae$setPlanningLogEnabled(boolean enabled) {
    }

    /** @deprecated Logging is controlled by the global Calculating debug configuration now. */
    @Deprecated
    default boolean neoecoae$isSubmissionLogEnabled() {
        return false;
    }

    /** @deprecated Logging is controlled by the global Calculating debug configuration now. */
    @Deprecated
    default void neoecoae$setSubmissionLogEnabled(boolean enabled) {
    }

    boolean neoecoae$hasComputationHost();

    /**
     * Item ids selected by computation interfaces for component-insensitive planning.
     */
    Set<ResourceLocation> neoecoae$getFuzzyPlanningItemIds();

    /**
     * Whether a calculation entering through AE2's normal planning path should opt into ECO. Other planners remain
     * free to handle the marked request before AE2 creates a calculation; ECO never cancels a future they return.
     */
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
