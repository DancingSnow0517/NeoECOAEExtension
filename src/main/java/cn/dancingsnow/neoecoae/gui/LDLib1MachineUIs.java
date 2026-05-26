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

    private LDLib1MachineUIs() {
    }

    public static ModularUI createIntegratedWorkingStationUI(ECOIntegratedWorkingStationBlockEntity be, Player player) {
        int width = 198;
        int height = 222;
        var ui = new ModularUI(width, height, be, player).background(panel());

        ui.widget(label(8, 8, Component.translatable("block.neoecoae.integrated_working_station"), 0xFFFFFFFF));
        ui.widget(label(8, 24, Component.literal("Progress"), 0xFFB8C7D9));
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

        ui.widget(label(8, 48, Component.literal("Fluids"), 0xFFB8C7D9));
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

        ui.widget(label(62, 48, Component.literal("Input"), 0xFFB8C7D9));
        addInventory(ui, be.getInput().toContainer(), 9, 62, 62, 3, true, true);

        ui.widget(label(124, 48, Component.literal("Output"), 0xFFB8C7D9));
        addInventory(ui, be.getOutput().toContainer(), 1, 124, 62, 1, false, true);

        ui.widget(label(150, 48, Component.literal("Upgrades"), 0xFFB8C7D9));
        addInventory(ui, be.getUpgrades().toContainer(), 4, 150, 62, 2, true, true);

        ui.widget(new ButtonWidget(
            124,
            92,
            56,
            18,
            new TextTexture(() -> autoExportText(be)).setColor(0xFFFFFFFF).setWidth(54),
            data -> {
                if (!data.isRemote) {
                    be.toggleAutoExport();
                }
            }
        ).setHoverTooltips(Component.translatable("gui.neoecoae.integrated_working_station.allow_outputs")));

        ui.widget(label(8, 124, Component.translatable("container.inventory"), 0xFFFFFFFF));
        addPlayerInventorySlots(ui, player, 8, 138);
        return ui;
    }

    public static ModularUI createCraftingControllerUI(ECOCraftingSystemBlockEntity be, Player player) {
        int width = 210;
        int height = 246;
        var ui = new ModularUI(width, height, be, player).background(panel());

        ui.widget(label(8, 8, be.getBlockState().getBlock().getName(), 0xFFFFFFFF));
        int y = 26;
        ui.widget(dynamicLabel(8, y, () -> boolLine("gui.neoecoae.crafting.enable_overlock", be.isOverclocked()), 0xFFB8C7D9));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> boolLine("gui.neoecoae.crafting.enable_active_cooling", be.isActiveCooling()), 0xFFB8C7D9));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> Component.translatable(
            "gui.neoecoae.crafting.coolant_amount",
            Tooltips.ofNumber(be.getCoolant()),
            Tooltips.ofNumber(ECOCraftingSystemBlockEntity.MAX_COOLANT)
        ).getString(), 0xFFB8C7D9));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> Component.translatable(
            "gui.neoecoae.crafting.working_threads",
            be.getRunningThreadCount(),
            be.getAvailableThreads(),
            be.getAvailableThreads() <= 0 ? 0 : (int) (be.getRunningThreadCount() * 100.0f / be.getAvailableThreads())
        ).getString(), 0xFFB8C7D9));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> Component.translatable("gui.neoecoae.crafting.parallel_core_count", be.getParallelCount()).getString(), 0xFFB8C7D9));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> Component.translatable("gui.neoecoae.crafting.pattern_bus_count", be.getPatternBusCount()).getString(), 0xFFB8C7D9));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> Component.translatable("gui.neoecoae.crafting.worker_count", be.getWorkerCount()).getString(), 0xFFB8C7D9));
        y += 12;
        ui.widget(dynamicLabel(8, y, () -> Component.translatable("gui.neoecoae.crafting.overclock_status", be.getOverlockTimes(), be.getEffectiveOverclockTimes()).getString(), 0xFFB8C7D9));
        y += 14;
        ui.widget(dynamicLabel(8, y, () -> be.getPreviewStatusComponent().getString(), 0xFFFFFFFF));

        ui.widget(new ButtonWidget(
            8,
            126,
            52,
            16,
            new TextTexture(Component.translatable("gui.neoecoae.multiblock.preview").getString()).setColor(0xFFFFFFFF).setWidth(50),
            data -> {
                if (!data.isRemote) {
                    be.previewStructure(player);
                }
            }
        ));
        ui.widget(new ButtonWidget(
            64,
            126,
            52,
            16,
            new TextTexture(Component.translatable("gui.neoecoae.multiblock.build").getString()).setColor(0xFFFFFFFF).setWidth(50),
            data -> {
                if (!data.isRemote) {
                    be.autoBuild(player);
                }
            }
        ));
        ui.widget(new ButtonWidget(
            120,
            126,
            64,
            16,
            new TextTexture(Component.translatable("gui.neoecoae.crafting.clear_coolant").getString()).setColor(0xFFFFFFFF).setWidth(62),
            data -> {
                if (!data.isRemote) {
                    be.clearCoolant();
                }
            }
        ));

        ui.widget(label(8, 150, Component.translatable("container.inventory"), 0xFFFFFFFF));
        addPlayerInventorySlots(ui, player, 8, 164);
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
