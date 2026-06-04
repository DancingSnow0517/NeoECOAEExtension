package cn.dancingsnow.neoecoae.network;

import net.minecraft.core.BlockPos;

/**
 * Read-only snapshot of Crafting Controller UI state sent from server to client.
 * <p>
 * Provides a summary of the crafting multiblock status: formation, active state,
 * worker/parallel/pattern counts, thread utilization, overclock/cooling flags,
 * and multi-block builder preview stats. Intentionally omits per-worker and
 * per-pattern details (reserved for future phases). No energy fields are
 * included because the crafting controller does not store AE energy directly.
 * </p>
 */
public record NECraftingUiState(
        BlockPos pos,
        boolean formed,
        boolean active,
        int workerCount,
        int parallelCount,
        int patternBusCount,
        int threadCount,
        int runningThreadCount,
        boolean overclocked,
        boolean activeCooling,
        boolean autoClearCoolingWaste,
        int selectedBuildLength,
        boolean buildInProgress,
        int previewMissingBlocks,
        int previewConflictBlocks,
        int previewReusedBlocks,
        int previewRequiredItems,
        String previewStatusKey,
        int previewStatusArg1,
        int previewStatusArg2) {
    public static NECraftingUiState empty(BlockPos pos) {
        return new NECraftingUiState(
                pos,
                false,
                false,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                1,
                false,
                0,
                0,
                0,
                0,
                "gui.neoecoae.multiblock.status.idle",
                0,
                0);
    }
}
