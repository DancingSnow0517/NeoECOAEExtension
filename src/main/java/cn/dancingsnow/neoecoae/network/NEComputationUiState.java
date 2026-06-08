package cn.dancingsnow.neoecoae.network;

import appeng.api.config.CpuSelectionMode;
import net.minecraft.core.BlockPos;

/**
 * Read-only snapshot of Computation Controller UI state sent from server to client.
 * <p>
 * Provides a lightweight summary of the computation multiblock status:
 * formation, active state, thread usage, storage bytes, parallel cores,
 * accelerator total, and CPU auto-selection mode. Intentionally omits
 * per-CPU detail and energy fields (not available on
 * {@link cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity}).
 * </p>
 */
public record NEComputationUiState(
        BlockPos pos,
        boolean formed,
        boolean active,
        int usedThreads,
        int maxThreads,
        long availableStorage,
        long totalStorage,
        /** Number of parallel core blocks in the cluster. */
        int parallelCount,
        /** Sum of accelerators across all parallel cores (from tier data). */
        int accelerators,
        /** CPU auto-selection mode for this computation cluster. */
        CpuSelectionMode cpuSelectionMode) {
    public static NEComputationUiState empty(BlockPos pos) {
        return new NEComputationUiState(pos, false, false, 0, 0, 0, 0, 0, 0, CpuSelectionMode.ANY);
    }
}
