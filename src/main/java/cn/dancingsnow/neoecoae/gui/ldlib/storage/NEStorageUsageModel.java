package cn.dancingsnow.neoecoae.gui.ldlib.storage;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import net.minecraft.util.Mth;

/** Derived values displayed by the right-hand system load panel. */
public final class NEStorageUsageModel {
    public static MatrixLoad highestMatrixLoad(NEStorageUiState state) {
        long used = 0L;
        long total = 0L;
        double highestPercent = -1.0D;
        for (var matrix : state.matrixStates()) {
            if (!matrix.hasMatrix() || matrix.totalBytes() <= 0L) {
                continue;
            }
            double percent = percent(matrix.usedBytes(), matrix.totalBytes());
            if (percent > highestPercent) {
                highestPercent = percent;
                used = matrix.usedBytes();
                total = matrix.totalBytes();
            }
        }
        return total <= 0L ? MatrixLoad.EMPTY : new MatrixLoad(used, total);
    }

    public static int idleMatrixCount(NEStorageUiState state) {
        int count = 0;
        for (var matrix : state.matrixStates()) {
            if (matrix.hasMatrix() && matrix.usedBytes() <= 0L && matrix.usedTypes() <= 0L) {
                count++;
            }
        }
        return count;
    }

    public static double percent(long used, long max) {
        if (max <= 0L) {
            return 0.0D;
        }
        return Mth.clamp((double) used / (double) max, 0.0D, 1.0D);
    }

    public record MatrixLoad(long used, long total) {
        private static final MatrixLoad EMPTY = new MatrixLoad(0L, 0L);
    }

    private NEStorageUsageModel() {}
}
