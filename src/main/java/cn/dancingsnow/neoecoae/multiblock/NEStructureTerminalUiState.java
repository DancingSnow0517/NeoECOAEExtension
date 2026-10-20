package cn.dancingsnow.neoecoae.multiblock;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Read-only snapshot of multiblock build state sent from server to client
 * for the Structure Terminal UI.
 */
public record NEStructureTerminalUiState(
        BlockPos hostPos,
        String structureName,
        boolean formed,
        boolean buildInProgress,
        int selectedBuildLength,
        int minBuildLength,
        int maxBuildLength,
        int previewMissingBlocks,
        int previewConflictBlocks,
        int previewReusedBlocks,
        int previewRequiredItems,
        int placedBlocks,
        int totalBlocks,
        String previewStatusKey,
        int previewStatusArg1,
        int previewStatusArg2,
        List<BuildMaterialEntry> materials) {
    public static NEStructureTerminalUiState empty(BlockPos hostPos) {
        return new NEStructureTerminalUiState(
                hostPos,
                "",
                false,
                false,
                1,
                1,
                16,
                0,
                0,
                0,
                0,
                0,
                0,
                "gui.neoecoae.multiblock.status.idle",
                0,
                0,
                List.of());
    }

    /**
     * A single material entry showing what item is needed and how many
     * the player currently has.
     */
    public record BuildMaterialEntry(ItemStack item, int required, int available) {
        public int missing() {
            return Math.max(0, required - available);
        }
    }
}
