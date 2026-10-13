package cn.dancingsnow.neoecoae.multiblock.network;

import appeng.api.networking.IGrid;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/** Shared state logic for ECO system hosts that expose network switches and pattern-substitution settings. */
public final class NetworkSwitchHostSupport {
    private NetworkSwitchHostSupport() {
    }

    /** Whether the switch property is present on the block state and set to true. */
    public static boolean isSwitchOn(BlockState state, BooleanProperty property) {
        return state.hasProperty(property) && state.getValue(property);
    }

    /** Grid-wide substitution flag when the grid has ECO settings, otherwise the host's local value. */
    public static boolean isIgnoringPatternSubstitutions(@Nullable IGrid grid, boolean localValue) {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(grid);
        return settings != null ? settings.neoecoae$isIgnoringPatternSubstitutions() : localValue;
    }

    /** Substitution pattern count of the grid, or 0 when the grid has no ECO settings. */
    public static int substitutionPatternCount(@Nullable IGrid grid) {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(grid);
        return settings == null ? 0 : settings.neoecoae$getSubstitutionPatternCount();
    }

    /**
     * Flips the grid-wide substitution flag.
     *
     * @return false when the grid has no ECO settings, meaning the caller must toggle its local value instead
     */
    public static boolean toggleGridIgnoringPatternSubstitutions(@Nullable IGrid grid) {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(grid);
        if (settings == null) {
            return false;
        }
        settings.neoecoae$setIgnoringPatternSubstitutions(!settings.neoecoae$isIgnoringPatternSubstitutions());
        return true;
    }
}
