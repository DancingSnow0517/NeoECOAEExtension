package cn.dancingsnow.neoecoae.api.me.planning;

import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable options captured for one explicit ECO planning request.
 */
public record ECOPlannerOptions(boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                                Set<ResourceLocation> fuzzyPlanningItemIds, boolean planningLogEnabled) {
    public ECOPlannerOptions {
        fuzzyPlanningItemIds = fuzzyPlanningItemIds == null ? Set.of() : Set.copyOf(fuzzyPlanningItemIds);
    }

    public ECOPlannerOptions(boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                             Set<ResourceLocation> fuzzyPlanningItemIds) {
        this(cyclePlanningEnabled, ignorePatternSubstitutions, fuzzyPlanningItemIds, false);
    }

    public static ECOPlannerOptions from(@Nullable ECOCraftingNetworkSettings settings) {
        if (settings == null) {
            return new ECOPlannerOptions(false, false, Set.of(), false);
        }
        return new ECOPlannerOptions(settings.neoecoae$isCyclePlanningEnabled(),
                settings.neoecoae$isIgnoringPatternSubstitutions(), settings.neoecoae$getFuzzyPlanningItemIds(),
                settings.neoecoae$isPlanningLogEnabled());
    }
}
