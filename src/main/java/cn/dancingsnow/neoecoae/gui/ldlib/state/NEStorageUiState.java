package cn.dancingsnow.neoecoae.gui.ldlib.state;

import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;

public record NEStorageUiState(
        BlockPos pos, List<NEStorageUiTypeState> typeStates, long storedEnergy, long maxEnergy, boolean formed) {
    public static NEStorageUiState empty(BlockPos pos) {
        return new NEStorageUiState(pos, Collections.emptyList(), 0, 0, false);
    }

    public long totalUsedTypes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum += ts.usedTypes();
        }
        return sum;
    }

    public long totalTypes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum += ts.totalTypes();
        }
        return sum;
    }

    public long totalUsedBytes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum += ts.usedBytes();
        }
        return sum;
    }

    public long totalBytes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum += ts.totalBytes();
        }
        return sum;
    }
}
