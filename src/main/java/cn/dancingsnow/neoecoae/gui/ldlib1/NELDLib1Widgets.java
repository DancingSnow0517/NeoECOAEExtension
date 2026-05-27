package cn.dancingsnow.neoecoae.gui.ldlib1;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidTransferWrapper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class NELDLib1Widgets {
    // Dark-background colors (title text, label text)
    public static final int TITLE_COLOR = 0xFF303040;
    public static final int LABEL_COLOR = 0xFF3F3D52;

    // Status text colors — use LIGHT_TEXT on dark panel backgrounds
    public static final int STATUS_COLOR_DARK_TEXT = 0xFF303040;
    public static final int STATUS_COLOR_LIGHT_TEXT = 0xFFE8E8F0;

    // Button text is always white
    public static final int BUTTON_TEXT_COLOR = 0xFFFFFFFF;

    private NELDLib1Widgets() {
    }

    public static LabelWidget title(int x, int y, Component component) {
        return label(x, y, component, TITLE_COLOR);
    }

    public static LabelWidget label(int x, int y, Component component) {
        return label(x, y, component, LABEL_COLOR);
    }

    public static LabelWidget label(int x, int y, Component component, int color) {
        return new LabelWidget(x, y, component).setTextColor(color).setDropShadow(false);
    }

    public static LabelWidget dynamicLabel(int x, int y, Supplier<String> text) {
        return dynamicLabel(x, y, text, STATUS_COLOR_DARK_TEXT);
    }

    /**
     * Create a dynamic label with light text — use on dark panel backgrounds.
     */
    public static LabelWidget dynamicLabelLight(int x, int y, Supplier<String> text) {
        return dynamicLabel(x, y, text, STATUS_COLOR_LIGHT_TEXT);
    }

    public static LabelWidget dynamicLabel(int x, int y, Supplier<String> text, int color) {
        return new LabelWidget(x, y, text).setTextColor(color).setDropShadow(false);
    }

    public static LabelWidget statusLine(int x, int y, Component label, Supplier<String> value) {
        return dynamicLabel(x, y, () -> label.getString() + ": " + value.get(), STATUS_COLOR_DARK_TEXT);
    }

    public static ButtonWidget button(int x, int y, int width, int height, Component text, Consumer<ClickData> action) {
        TextTexture texture = NELDLib1Textures
            .text(() -> text.getString(), BUTTON_TEXT_COLOR, Math.max(1, width - 4))
            .setBackgroundTexture(NELDLib1Textures.BUTTON);
        return new ButtonWidget(x, y, width, height, texture, action)
            .setHoverTexture(NELDLib1Textures.BUTTON_HOVER)
            .setClickedTexture(NELDLib1Textures.BUTTON_HIGHLIGHTED);
    }

    public static ButtonWidget toggleButton(
        int x,
        int y,
        int width,
        int height,
        Supplier<Boolean> enabled,
        IGuiTexture enabledTexture,
        IGuiTexture disabledTexture,
        Consumer<ClickData> action
    ) {
        IGuiTexture baseTexture = enabled.get() ? enabledTexture : disabledTexture;
        return new ButtonWidget(x, y, width, height, baseTexture, action)
            .setHoverTexture(NELDLib1Textures.BUTTON_HOVER);
    }

    public static SlotWidget machineSlot(Container container, int slot, int x, int y, boolean canPut, boolean canTake) {
        return new SlotWidget(container, slot, x, y, canPut, canTake).setBackgroundTexture(NELDLib1Textures.SLOT);
    }

    public static SlotWidget outputSlot(Container container, int slot, int x, int y) {
        return machineSlot(container, slot, x, y, false, true);
    }

    /**
     * Create a Pattern Bus slot widget that shows {@link NELDLib1Textures#PATTERN_OVERLAY}
     * only when the slot is empty. The overlay is drawn in the background layer so
     * real ItemStack icons are never covered.
     */
    public static SlotWidget patternSlot(Container container, int slot, int x, int y, boolean canPut, boolean canTake) {
        return new PatternSlotWidget(container, slot, x, y, canPut, canTake)
            .setEmptyOverlay(NELDLib1Textures.PATTERN_OVERLAY)
            .setBackgroundTexture(NELDLib1Textures.SLOT);
    }

    public static void addInventoryGrid(
        ModularUI ui,
        Container container,
        int size,
        int x,
        int y,
        int columns,
        boolean canPut,
        boolean canTake
    ) {
        for (int slot = 0; slot < size; slot++) {
            int col = slot % columns;
            int row = slot / columns;
            ui.widget(machineSlot(container, slot, x + col * NELDLib1Layout.SLOT, y + row * NELDLib1Layout.SLOT, canPut, canTake));
        }
    }

    public static void addPlayerInventory(ModularUI ui, Player player, int x, int y) {
        Container inventory = player.getInventory();
        for (int row = 0; row < NELDLib1Layout.PLAYER_INV_MAIN_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = col + (row + 1) * 9;
                ui.widget(new SlotWidget(
                    inventory,
                    slot,
                    x + col * NELDLib1Layout.SLOT,
                    y + row * NELDLib1Layout.SLOT,
                    true,
                    true
                ).setLocationInfo(true, false).setBackgroundTexture(NELDLib1Textures.SLOT));
            }
        }
        for (int col = 0; col < 9; col++) {
            ui.widget(new SlotWidget(
                inventory,
                col,
                x + col * NELDLib1Layout.SLOT,
                y + NELDLib1Layout.HOTBAR_Y_OFFSET,
                true,
                true
            ).setLocationInfo(true, true).setBackgroundTexture(NELDLib1Textures.SLOT));
        }
    }

    public static TankWidget tank(FluidTank tank, int x, int y, int width, int height) {
        return new TankWidget(new FluidTransferWrapper(tank), 0, x, y, width, height, true, true)
            .setShowAmount(true)
            .setBackground(NELDLib1Textures.INVENTORY_BORDER);
    }

    public static ProgressWidget verticalProgress(DoubleSupplier progress, int x, int y, int width, int height, IGuiTexture filled) {
        return new ProgressWidget(progress, x, y, width, height)
            .setProgressTexture(NELDLib1Textures.BAR_CONTAINER, filled)
            .setFillDirection(ProgressTexture.FillDirection.UP_TO_DOWN);
    }

    public static ProgressWidget horizontalProgress(DoubleSupplier progress, int x, int y, int width, int height, IGuiTexture filled) {
        return new ProgressWidget(progress, x, y, width, height)
            .setProgressTexture(NELDLib1Textures.BAR_CONTAINER, filled)
            .setFillDirection(ProgressTexture.FillDirection.LEFT_TO_RIGHT);
    }

    public static ImageWidget image(int x, int y, int width, int height, IGuiTexture texture) {
        return new ImageWidget(x, y, width, height, texture);
    }

    public static ImageWidget image(int x, int y, int width, int height, Supplier<IGuiTexture> texture) {
        return new ImageWidget(x, y, width, height, texture);
    }
}
