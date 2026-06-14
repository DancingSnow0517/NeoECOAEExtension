package cn.dancingsnow.neoecoae.client;

import appeng.api.storage.cells.CellState;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;

public final class NEItemColors {
    private static final int STATUS_LIGHT_TINT_INDEX = 2;
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;

    private NEItemColors() {}

    public static void registerBaseCells(RegisterColorHandlersEvent.Item event) {
        registerEcoCellStatusLights(
                event,
                NEItems.ECO_ITEM_CELL_16M.get(),
                NEItems.ECO_ITEM_CELL_64M.get(),
                NEItems.ECO_ITEM_CELL_256M.get(),
                NEItems.ECO_FLUID_CELL_16M.get(),
                NEItems.ECO_FLUID_CELL_64M.get(),
                NEItems.ECO_FLUID_CELL_256M.get());
    }

    public static void registerEcoCellStatusLights(RegisterColorHandlersEvent.Item event, ItemLike... items) {
        event.register(NEItemColors::getEcoCellColor, items);
    }

    private static int getEcoCellColor(ItemStack stack, int tintIndex) {
        if (tintIndex != STATUS_LIGHT_TINT_INDEX) {
            return DEFAULT_COLOR;
        }

        var cell = ECOStorageCellItem.getCellInventory(stack);
        if (cell == null) {
            return CellState.ABSENT.getStateColor();
        }
        return 0xFF000000 | cell.getStatus().getStateColor();
    }
}
