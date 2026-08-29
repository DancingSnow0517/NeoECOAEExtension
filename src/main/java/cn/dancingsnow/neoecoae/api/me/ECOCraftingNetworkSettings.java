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

    static @Nullable ECOCraftingNetworkSettings of(@Nullable IGrid grid) {
        if (grid != null && grid.getCraftingService() instanceof ECOCraftingNetworkSettings settings) {
            return settings;
        }
        return null;
    }
}
