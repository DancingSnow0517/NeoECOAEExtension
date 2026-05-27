package cn.dancingsnow.neoecoae.network;

import net.minecraft.core.BlockPos;

/**
 * Read-only snapshot of Storage Controller UI state sent from server to client.
 */
public record NEStorageUiState(
    BlockPos pos,
    long usedTypes,
    long totalTypes,
    long usedBytes,
    long totalBytes,
    long storedEnergy,
    long maxEnergy,
    boolean formed
) {
    public static NEStorageUiState empty(BlockPos pos) {
        return new NEStorageUiState(pos, 0, 0, 0, 0, 0, 0, false);
    }
}
