package cn.dancingsnow.neoecoae.gui.ldlib.state;

import net.minecraft.core.BlockPos;

public record NECraftingModuleCell(int column, Row row, int tier, BlockPos pos) {
    public enum Row {
        UPPER_PARALLEL,
        WORKER,
        LOWER_PARALLEL
    }
}
