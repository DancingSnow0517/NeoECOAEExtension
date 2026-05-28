package cn.dancingsnow.neoecoae.network;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.List;

/**
 * Read-only snapshot of Storage Controller UI state sent from server to client.
 * <p>
 * Storage capacity is reported per cell type via {@link #typeStates()}.
 * Aggregate totals are available through the convenience accessors
 * {@link #totalUsedTypes()}, {@link #totalTypes()}, {@link #totalUsedBytes()},
 * and {@link #totalBytes()}, but the primary UI display uses the per-type list.
 * </p>
 */
public record NEStorageUiState(
    BlockPos pos,
    List<NEStorageUiTypeState> typeStates,
    long storedEnergy,
    long maxEnergy,
    boolean formed
) {
    public static NEStorageUiState empty(BlockPos pos) {
        return new NEStorageUiState(pos, Collections.emptyList(), 0, 0, false);
    }

    /** Aggregate used types across all cell types. */
    public long totalUsedTypes() {
        long sum = 0;
        for (var ts : typeStates) { sum += ts.usedTypes(); }
        return sum;
    }

    /** Aggregate total types across all cell types. */
    public long totalTypes() {
        long sum = 0;
        for (var ts : typeStates) { sum += ts.totalTypes(); }
        return sum;
    }

    /** Aggregate used bytes across all cell types. */
    public long totalUsedBytes() {
        long sum = 0;
        for (var ts : typeStates) { sum += ts.usedBytes(); }
        return sum;
    }

    /** Aggregate total bytes across all cell types. */
    public long totalBytes() {
        long sum = 0;
        for (var ts : typeStates) { sum += ts.totalBytes(); }
        return sum;
    }
}
