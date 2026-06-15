package cn.dancingsnow.neoecoae.gui.ldlib.support;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;

public final class NEPlayerInventoryWidgets {
    public static final int SLOT_SIZE = 18;

    private static final int INVENTORY_ROWS = 3;
    private static final int INVENTORY_COLUMNS = 9;

    private NEPlayerInventoryWidgets() {}

    public static void addPlayerInventorySlots(
            WidgetGroup owner, Inventory inventory, int inventoryX, int inventoryY, int hotbarY) {
        for (int row = 0; row < INVENTORY_ROWS; row++) {
            for (int col = 0; col < INVENTORY_COLUMNS; col++) {
                owner.addWidget(new SlotWidget(
                                inventory,
                                col + row * INVENTORY_COLUMNS + INVENTORY_COLUMNS,
                                inventoryX + col * SLOT_SIZE,
                                inventoryY + row * SLOT_SIZE,
                                true,
                                true)
                        .setBackgroundTexture(IGuiTexture.EMPTY)
                        .setLocationInfo(true, false));
            }
        }
        for (int col = 0; col < INVENTORY_COLUMNS; col++) {
            owner.addWidget(new SlotWidget(inventory, col, inventoryX + col * SLOT_SIZE, hotbarY, true, true)
                    .setBackgroundTexture(IGuiTexture.EMPTY)
                    .setLocationInfo(true, true));
        }
    }

    public static void drawPlayerInventorySlots(
            GuiGraphics graphics,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            int inventoryBgX,
            int inventoryBgY,
            int hotbarBgY) {
        for (int row = 0; row < INVENTORY_ROWS; row++) {
            for (int col = 0; col < INVENTORY_COLUMNS; col++) {
                NELDLibAe2StyleRenderer.drawAeSlot(
                        graphics,
                        screenX.applyAsInt(inventoryBgX + col * SLOT_SIZE),
                        screenY.applyAsInt(inventoryBgY + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < INVENTORY_COLUMNS; col++) {
            NELDLibAe2StyleRenderer.drawAeSlot(
                    graphics, screenX.applyAsInt(inventoryBgX + col * SLOT_SIZE), screenY.applyAsInt(hotbarBgY));
        }
    }
}
