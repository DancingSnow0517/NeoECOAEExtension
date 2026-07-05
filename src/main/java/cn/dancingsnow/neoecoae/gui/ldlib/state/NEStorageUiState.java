package cn.dancingsnow.neoecoae.gui.ldlib.state;

import com.google.common.math.LongMath;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;

public record NEStorageUiState(
        BlockPos pos,
        List<NEStorageUiTypeState> typeStates,
        List<NEStorageUiMatrixState> matrixStates,
        List<NEStorageHugeStackState> hugeStacks,
        long storedEnergy,
        long maxEnergy,
        boolean formed,
        boolean infiniteSlotVisible,
        boolean infiniteMode,
        int infiniteComponentCount,
        boolean canTakeInfiniteComponent,
        boolean infiniteDomainEmpty) {
    public static NEStorageUiState empty(BlockPos pos) {
        return empty(pos, false);
    }

    public static NEStorageUiState empty(BlockPos pos, boolean infiniteSlotVisible) {
        return new NEStorageUiState(
                pos,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                0,
                false,
                infiniteSlotVisible,
                false,
                0,
                true,
                true);
    }

    public long totalUsedTypes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum = saturatedAdd(sum, ts.usedTypes());
        }
        return sum;
    }

    public long totalTypes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum = saturatedAdd(sum, ts.totalTypes());
        }
        return sum;
    }

    public long totalUsedBytes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum = saturatedAdd(sum, ts.usedBytes());
        }
        return sum;
    }

    public long totalBytes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum = saturatedAdd(sum, ts.totalBytes());
        }
        return sum;
    }

    private static long saturatedAdd(long left, long right) {
        return LongMath.saturatedAdd(left, right);
    }
}
