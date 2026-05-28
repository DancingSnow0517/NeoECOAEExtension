package cn.dancingsnow.neoecoae.network;

import net.minecraft.core.BlockPos;

/**
 * Read-only snapshot of Computation Controller UI state sent from server to client.
 * <p>
 * Provides a lightweight summary of the computation multiblock status:
 * formation, thread usage, storage bytes, parallelism, and accelerator count.
 * Intentionally omits per-CPU detail and energy fields (not available on
 * {@link cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity}).
 * </p>
 */
public record NEComputationUiState(
    BlockPos pos,
    boolean formed,
    int usedThreads,
    int maxThreads,
    long availableStorage,
    long totalStorage,
    int parallelCount,
    int accelerators
) {
    public static NEComputationUiState empty(BlockPos pos) {
        return new NEComputationUiState(pos, false, 0, 0, 0, 0, 0, 0);
    }
}
