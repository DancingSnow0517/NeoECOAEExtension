package cn.dancingsnow.neoecoae.gui.ldlib.state;

import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import net.minecraft.core.BlockPos;

public record NEStorageInterfaceUiState(
        BlockPos pos,
        boolean formed,
        ECOStorageInterfaceMode mode,
        long exportedLastTick,
        long exportedTotal,
        boolean targetOnline,
        boolean hasController) {
    public static NEStorageInterfaceUiState empty(BlockPos pos) {
        return new NEStorageInterfaceUiState(pos, false, ECOStorageInterfaceMode.STORAGE, 0L, 0L, false, false);
    }
}
