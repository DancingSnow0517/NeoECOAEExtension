package cn.dancingsnow.neoecoae.api.me.planning;

import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Options captured for one explicit ECO planning request.
 *
 * <p>The legacy {@code ignorePatternSubstitutions} name now selects GTNH-style lite crafting:
 * one producer per material, without route retries, ordinary substitutions, or remainder reuse. Physical
 * byproducts share the exact output ledger; missing producer views are filled once from already selected
 * patterns, and acyclic demands allow at most one supplemental expansion. Enabled cycles use the existing
 * cycle solver on the fixed route; disabled cycles close only against captured stock and verified output
 * allocations. Explicit processing-input item-ID rules do not switch to full planning.</p>
 *
 * <p>The legacy {@code planningLogEnabled} component remains for compatibility, but stage logging is now controlled
 * by the global {@code Debug > Calculating} configuration.</p>
 */
public record ECOPlannerOptions(boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                                Set<ResourceLocation> fuzzyPlanningItemIds, boolean planningLogEnabled) {
    public ECOPlannerOptions {
        fuzzyPlanningItemIds = fuzzyPlanningItemIds == null ? Set.of() : fuzzyPlanningItemIds;
    }

    public ECOPlannerOptions(boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                             Set<ResourceLocation> fuzzyPlanningItemIds) {
        this(cyclePlanningEnabled, ignorePatternSubstitutions, fuzzyPlanningItemIds, false);
    }

    public static ECOPlannerOptions from(@Nullable ECOCraftingNetworkSettings settings) {
        if (settings == null) {
            return new ECOPlannerOptions(false, true, Set.of());
        }
        return new ECOPlannerOptions(settings.neoecoae$isCyclePlanningEnabled(),
                settings.neoecoae$isIgnoringPatternSubstitutions(), settings.neoecoae$getFuzzyPlanningItemIds());
    }
}
