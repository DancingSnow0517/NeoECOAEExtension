package cn.dancingsnow.neoecoae.client;

import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;

public final class NEItemColors {
    private static final int STATUS_LIGHT_TINT_INDEX = 2;

    private NEItemColors() {
    }

    public static int getCellColor(ItemStack stack, int tintIndex) {
        if (tintIndex != STATUS_LIGHT_TINT_INDEX) {
            return 0xFFFFFFFF;
        }

        var cell = StorageCells.getCellInventory(stack, null);
        var state = cell != null ? cell.getStatus() : CellState.EMPTY;
        return FastColor.ARGB32.opaque(state.getStateColor());
    }
}
