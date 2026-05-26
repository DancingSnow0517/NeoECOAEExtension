package cn.dancingsnow.neoecoae.gui;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidTransferWrapper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;

public final class LDLib1MachineUIs {
    private static final ResourceTexture SLOT = texture("textures/gui/slot.png");
    private static final ResourceTexture BAR = texture("textures/gui/bar.png");
    private static final ResourceTexture BAR_CONTAINER = texture("textures/gui/bar_container.png");
    private static final int TITLE_COLOR = 0xFF303040;
    private static final int LABEL_COLOR = 0xFF3F3D52;
    private static final int STATUS_COLOR = 0xFF303040;
    private static final int BUTTON_TEXT_COLOR = 0xFFFFFFFF;

    private LDLib1MachineUIs() {
    }

    public static ModularUI createIntegratedWorkingStationUI(ECOIntegratedWorkingStationBlockEntity be, Player player) {
        int width = 230;
        int height = 238;
        var ui = new ModularUI(width, height, be, player).background(panel());

        ui.widget(label(8, 8, Component.translatable("block.neoecoae.integrated_working_station"), TITLE_COLOR));
        ui.widget(label(8, 24, Component.literal("Progress"), LABEL_COLOR));
        ui.widget(new ProgressWidget(
            () -> be.getMaxProcessingTime() <= 0 ? 0.0 : be.getProcessingTime() / (double) be.getMaxProcessingTime(),
            70,
            21,
            6,
            18
        )
            .setProgressTexture(BAR_CONTAINER, BAR)
            .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
            .setDynamicHoverTips(value -> "%d%%".formatted((int) Math.round(value * 100))));

        ui.widget(label(8, 48, Component.literal("Fluids"), LABEL_COLOR));
        ui.widget(new TankWidget(
            new FluidTransferWrapper(be.getInputTank()),
            0,
            8,
            62,
            18,
            54,
            true,
            true
        ).setShowAmount(true).setBackground(SLOT).setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP));
        ui.widget(new TankWidget(
            new FluidTransferWrapper(be.getOutputTank()),
            0,
            32,
            62,
            18,
            54,
            true,
            true
        ).setShowAmount(true).setBackground(SLOT).setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP));

        ui.widget(label(62, 48, Component.literal("Input"), LABEL_COLOR));
        addInventory(ui, be.getInput().toContainer(), 9, 62, 62, 3, true, true);

        ui.widget(label(130, 48, Component.literal("Output"), LABEL_COLOR));
        addInventory(ui, be.getOutput().toContainer(), 1, 130, 62, 1, false, true);

        ui.widget(label(178, 48, Component.literal("Upgrades"), LABEL_COLOR));
        addInventory(ui, be.getUpgrades().toContainer(), 4, 178, 62, 1, true, true);

        ui.widget(new ButtonWidget(
            130,
            112,
            84,
            18,
            new TextTexture(() -> "Auto Export: " + autoExportText(be)).setColor(BUTTON_TEXT_COLOR).setWidth(82),
            data -> {
                if (!data.isRemote) {
                    be.toggleAutoExport();
                }
            }
        ).setHoverTooltips(Component.translatable("gui.neoecoae.integrated_working_station.allow_outputs")));

        ui.widget(label(8, 138, Component.translatable("container.inventory"), TITLE_COLOR));
        addPlayerInventorySlots(ui, player, 8, 152);
        return ui;
    }

    public static ModularUI createCraftingControllerUI(ECOCraftingSystemBlockEntity be, Player player) {
        int width = 210;
        int height = 260;
        var ui = new ModularUI(width, height, be, player).background(panel());

        ui.widget(label(8, 8, be.getBlockState().getBlock().getName(), TITLE_COLOR));
        int y = 26;
        ui.widget(dynamicLabel(8, y, () -> boolLine("gui.neoecoae.crafting.enable_overlock", be.isOverclocked()), STATUS_COLOR));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> boolLine("gui.neoecoae.crafting.enable_active_cooling", be.isActiveCooling()), STATUS_COLOR));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> Component.translatable(
            "gui.neoecoae.crafting.coolant_amount",
            Tooltips.ofNumber(be.getCoolant()),
            Tooltips.ofNumber(ECOCraftingSystemBlockEntity.MAX_COOLANT)
        ).getString(), STATUS_COLOR));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> Component.translatable(
            "gui.neoecoae.crafting.working_threads",
            be.getRunningThreadCount(),
            be.getAvailableThreads(),
            be.getAvailableThreads() <= 0 ? 0 : (int) (be.getRunningThreadCount() * 100.0f / be.getAvailableThreads())
        ).getString(), STATUS_COLOR));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> Component.translatable("gui.neoecoae.crafting.parallel_core_count", be.getParallelCount()).getString(), STATUS_COLOR));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> Component.translatable("gui.neoecoae.crafting.pattern_bus_count", be.getPatternBusCount()).getString(), STATUS_COLOR));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> Component.translatable("gui.neoecoae.crafting.worker_count", be.getWorkerCount()).getString(), STATUS_COLOR));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> "Overclock: %d / %d".formatted(be.getOverlockTimes(), be.getEffectiveOverclockTimes()), STATUS_COLOR));
        y += 14;
        ui.widget(dynamicLabel(8, y, () -> be.getPreviewStatusComponent().getString(), STATUS_COLOR));

        ui.widget(new ButtonWidget(
            8,
            138,
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
            138,
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
            138,
            64,
            16,
            new TextTexture(Component.translatable("gui.neoecoae.crafting.clear_coolant").getString()).setColor(BUTTON_TEXT_COLOR).setWidth(62),
            data -> {
                if (!data.isRemote) {
                    be.clearCoolant();
                }
            }
        ));

        ui.widget(label(8, 162, Component.translatable("container.inventory"), TITLE_COLOR));
        addPlayerInventorySlots(ui, player, 8, 176);
        return ui;
    }

    private static String autoExportText(ECOIntegratedWorkingStationBlockEntity be) {
        return Component.translatable(be.isAutoExportEnabled()
            ? "gui.neoecoae.integrated_working_station.allow_outputs.enabled"
            : "gui.neoecoae.integrated_working_station.allow_outputs.disabled"
        ).getString();
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
