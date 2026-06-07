package cn.dancingsnow.neoecoae.network;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

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
        int previewStatusArg2,
        long energyUsage,
        long coolantAmount,
        long coolantCapacity,
        int availableThreads,
        int effectiveParallel,
        List<ItemStack> workerCraftOutputs,
        List<Integer> parallelCoreTiers) {
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
                0,
                0L,
                0L,
                0L,
                0,
                0,
                List.of(),
                List.of());
    }
}
