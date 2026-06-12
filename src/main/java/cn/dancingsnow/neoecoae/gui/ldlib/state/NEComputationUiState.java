package cn.dancingsnow.neoecoae.gui.ldlib.state;

import appeng.api.config.CpuSelectionMode;
import java.util.List;
import net.minecraft.core.BlockPos;

public record NEComputationUiState(
        BlockPos pos,
        boolean formed,
        boolean active,
        int usedThreads,
        int maxThreads,
        long availableStorage,
        long totalStorage,
        int parallelCount,
        int accelerators,
        CpuSelectionMode cpuSelectionMode,
        List<NECraftingRecipeUiEntry> recipeEntries) {
    public static NEComputationUiState empty(BlockPos pos) {
        return new NEComputationUiState(pos, false, false, 0, 0, 0, 0, 0, 0, CpuSelectionMode.ANY, List.of());
    }
}
