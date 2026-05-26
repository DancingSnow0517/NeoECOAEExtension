package cn.dancingsnow.neoecoae.gui;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;

public final class LDLib1MachineUIs {
    private static final ResourceTexture SLOT = texture("textures/gui/slot.png");
    private static final int TITLE_COLOR = 0xFF303040;
    private static final int STATUS_COLOR = 0xFF303040;
    private static final int BUTTON_TEXT_COLOR = 0xFFFFFFFF;

    private LDLib1MachineUIs() {
    }

    public static ModularUI createIntegratedWorkingStationUI(ECOIntegratedWorkingStationBlockEntity be, Player player) {
        int width = 198;
        int height = 214;
        var ui = new ModularUI(width, height, be, player).background(panel());

        ui.widget(label(8, 8, Component.translatable("block.neoecoae.integrated_working_station"), TITLE_COLOR));
        addInventory(ui, be.getInput().toContainer(), 9, 8, 28, 3, true, true);
        addInventory(ui, be.getOutput().toContainer(), 1, 80, 46, 1, false, true);
        addInventory(ui, be.getUpgrades().toContainer(), 4, 116, 28, 1, true, true);

        ui.widget(label(8, 116, Component.translatable("container.inventory"), TITLE_COLOR));
        addPlayerInventorySlots(ui, player, 8, 130);
        return ui;
    }

    public static ModularUI createCraftingControllerUI(ECOCraftingSystemBlockEntity be, Player player) {
        int width = 198;
        int height = 214;
        var ui = new ModularUI(width, height, be, player).background(panel());

        ui.widget(label(8, 8, be.getBlockState().getBlock().getName(), TITLE_COLOR));
        ui.widget(dynamicLabel(8, 26, () -> "Overclock: %d / %d".formatted(be.getOverlockTimes(), be.getEffectiveOverclockTimes()), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 38, () -> boolLine("gui.neoecoae.crafting.enable_active_cooling", be.isActiveCooling()), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 50, () -> Component.translatable(
            "gui.neoecoae.crafting.coolant_amount",
            Tooltips.ofNumber(be.getCoolant()),
            Tooltips.ofNumber(ECOCraftingSystemBlockEntity.MAX_COOLANT)
        ).getString(), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 62, () -> Component.translatable(
            "gui.neoecoae.crafting.working_threads",
            be.getRunningThreadCount(),
            be.getAvailableThreads(),
            be.getAvailableThreads() <= 0 ? 0 : (int) (be.getRunningThreadCount() * 100.0f / be.getAvailableThreads())
        ).getString(), STATUS_COLOR));

        ui.widget(new ButtonWidget(
            8,
            86,
            52,
            16,
            new TextTexture(Component.translatable("gui.neoecoae.multiblock.preview").getString()).setColor(BUTTON_TEXT_COLOR).setWidth(50),
            data -> {
                if (!data.isRemote) {
                    be.previewStructure(player);
                }
            }
        ));
        ui.widget(new ButtonWidget(
            64,
            86,
            52,
            16,
            new TextTexture(Component.translatable("gui.neoecoae.multiblock.build").getString()).setColor(BUTTON_TEXT_COLOR).setWidth(50),
            data -> {
                if (!data.isRemote) {
                    be.autoBuild(player);
                }
            }
        ));
        ui.widget(new ButtonWidget(
            120,
            86,
            64,
            16,
            new TextTexture(Component.translatable("gui.neoecoae.crafting.clear_coolant").getString()).setColor(BUTTON_TEXT_COLOR).setWidth(62),
            data -> {
                if (!data.isRemote) {
                    be.clearCoolant();
                }
            }
        ));

        ui.widget(label(8, 116, Component.translatable("container.inventory"), TITLE_COLOR));
        addPlayerInventorySlots(ui, player, 8, 130);
        return ui;
    }

    private static String boolLine(String key, boolean value) {
        return Component.translatable(key).getString() + Component.translatable(value
            ? "gui.neoecoae.integrated_working_station.allow_outputs.enabled"
            : "gui.neoecoae.integrated_working_station.allow_outputs.disabled"
        ).getString();
    }

    private static LabelWidget label(int x, int y, Component component, int color) {
        return new LabelWidget(x, y, component).setTextColor(color).setDropShadow(false);
    }

    private static LabelWidget dynamicLabel(int x, int y, java.util.function.Supplier<String> text, int color) {
        return new LabelWidget(x, y, text).setTextColor(color).setDropShadow(false);
    }

    private static void addInventory(
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
            ui.widget(new SlotWidget(
                container,
                slot,
                x + col * 18,
                y + row * 18,
                canPut,
                canTake
            ).setBackgroundTexture(SLOT));
        }
    }

    private static void addPlayerInventorySlots(ModularUI ui, Player player, int x, int y) {
        Container inventory = player.getInventory();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = col + (row + 1) * 9;
                ui.widget(new SlotWidget(
                    inventory,
                    slot,
                    x + col * 18,
                    y + row * 18,
                    true,
                    true
                ).setLocationInfo(true, false).setBackgroundTexture(SLOT));
            }
        }
        for (int col = 0; col < 9; col++) {
            ui.widget(new SlotWidget(
                inventory,
                col,
                x + col * 18,
                y + 58,
                true,
                true
            ).setLocationInfo(true, true).setBackgroundTexture(SLOT));
        }
    }

    private static ResourceTexture texture(String path) {
        return new ResourceTexture("neoecoae:" + path);
    }

    private static ResourceBorderTexture panel() {
        return new ResourceBorderTexture("neoecoae:textures/gui/background.png", 16, 16, 4, 4);
    }
}
