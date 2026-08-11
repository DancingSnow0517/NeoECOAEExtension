package cn.dancingsnow.neoecoae.gui.ldlib.state;

import appeng.api.config.CpuSelectionMode;
import java.util.List;
import net.minecraft.core.BlockPos;

public record NEComputationUiState(
        BlockPos pos,
        boolean formed,
        boolean active,
        int networkMemberCount,
        int networkMultiplier,
        boolean networkConnected,
        int networkFrequency,
        int usedThreads,
        int maxThreads,
        long availableStorage,
        long totalStorage,
        int parallelCount,
        int accelerators,
        int acceleratorLimit,
        int configuredAccelerators,
        boolean infiniteCapacity,
        boolean fastTaskPlanningEnabled,
        boolean batchFairSchedulingEnabled,
        CpuSelectionMode cpuSelectionMode,
        List<NECraftingRecipeUiEntry> recipeEntries) {
    public static NEComputationUiState empty(BlockPos pos) {
        return new NEComputationUiState(
                pos,
                false,
                false,
                0,
                1,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                true,
                false,
                CpuSelectionMode.ANY,
                List.of());
    }
}
