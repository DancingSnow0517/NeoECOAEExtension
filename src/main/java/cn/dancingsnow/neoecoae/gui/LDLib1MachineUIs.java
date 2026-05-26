package cn.dancingsnow.neoecoae.gui;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidTransferWrapper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public final class LDLib1MachineUIs {
    private static final ResourceTexture SLOT = texture("textures/gui/slot.png");
    private static final int TITLE_COLOR = 0xFF303040;
    private static final int STATUS_COLOR = 0xFF303040;
    private static final int BUTTON_TEXT_COLOR = 0xFFFFFFFF;

    private LDLib1MachineUIs() {
    }

    public static ModularUI createIntegratedWorkingStationUI(ECOIntegratedWorkingStationBlockEntity be, Player player) {
        int width = 198;
        int height = 222;
        var ui = new ModularUI(width, height, be, player).background(panel());

        ui.widget(label(8, 8, Component.translatable("block.neoecoae.integrated_working_station"), TITLE_COLOR));
        ui.widget(label(8, 24, Component.literal("Input"), STATUS_COLOR));
        addInventory(ui, be.getInput().toContainer(), 9, 8, 38, 3, true, true);
        ui.widget(label(72, 24, Component.literal("Output"), STATUS_COLOR));
        addInventory(ui, be.getOutput().toContainer(), 1, 72, 56, 1, false, true);
        ui.widget(label(104, 24, Component.literal("Upgrades"), STATUS_COLOR));
        addInventory(ui, be.getUpgrades().toContainer(), 4, 104, 38, 1, true, true);

        ui.widget(label(8, 116, Component.translatable("container.inventory"), TITLE_COLOR));
        addPlayerInventorySlots(ui, player, 8, 130);
        return ui;
    }

    public static ModularUI createCraftingControllerUI(ECOCraftingSystemBlockEntity be, Player player) {
        int width = 198;
        int height = 222;
        var ui = new ModularUI(width, height, be, player).background(panel());

        ui.widget(label(8, 8, be.getBlockState().getBlock().getName(), TITLE_COLOR));
        ui.widget(dynamicLabel(8, 28, () -> "Overclock: " + enabledText(be.isOverclocked()), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 40, () -> "Active Cooling: " + enabledText(be.isActiveCooling()), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 52, () -> "Threads: %d / %d".formatted(be.getRunningThreadCount(), be.getAvailableThreads()), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 64, () -> "Status: " + be.getPreviewStatusComponent().getString(), STATUS_COLOR));

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

    public static ModularUI createCraftingPatternBusUI(ECOCraftingPatternBusBlockEntity be, Player player) {
        int width = 198;
        int height = 222;
        var ui = new ModularUI(width, height, be, player).background(panel());

        ui.widget(label(8, 8, Component.translatable("block.neoecoae.crafting_pattern_bus"), TITLE_COLOR));
        ui.widget(label(8, 24, Component.literal("Patterns 1-36"), STATUS_COLOR));
        addInventory(ui, be.getTerminalPatternInventory().toContainer(), 36, 8, 38, 9, true, true);

        ui.widget(label(8, 116, Component.translatable("container.inventory"), TITLE_COLOR));
        addPlayerInventorySlots(ui, player, 8, 130);
        return ui;
    }

    public static ModularUI createFluidHatchUI(ECOFluidInputHatchBlockEntity be, Player player, String titleKey) {
        return createFluidHatchUI(be, player, titleKey, be.tank);
    }

    public static ModularUI createFluidHatchUI(ECOFluidOutputHatchBlockEntity be, Player player, String titleKey) {
        return createFluidHatchUI(be, player, titleKey, be.tank);
    }

    private static ModularUI createFluidHatchUI(com.lowdragmc.lowdraglib.gui.modular.IUIHolder holder, Player player, String titleKey, FluidTank tank) {
        int width = 198;
        int height = 222;
        var ui = new ModularUI(width, height, holder, player).background(panel());

        ui.widget(label(8, 8, Component.translatable(titleKey), TITLE_COLOR));
        ui.widget(new TankWidget(new FluidTransferWrapper(tank), 0, 8, 30, 18, 54, true, true)
            .setShowAmount(true)
            .setBackground(SLOT));
        ui.widget(dynamicLabel(34, 32, () -> "Fluid: " + tank.getFluid().getDisplayName().getString(), STATUS_COLOR));
        ui.widget(dynamicLabel(34, 44, () -> "Amount: %s / %s".formatted(
            Tooltips.ofNumber(tank.getFluidAmount()).getString(),
            Tooltips.ofNumber(tank.getCapacity()).getString()
        ), STATUS_COLOR));

        ui.widget(label(8, 116, Component.translatable("container.inventory"), TITLE_COLOR));
        addPlayerInventorySlots(ui, player, 8, 130);
        return ui;
    }

    public static ModularUI createComputationSystemUI(ECOComputationSystemBlockEntity be, Player player) {
        int width = 198;
        int height = 118;
        var ui = new ModularUI(width, height, be, player).background(panel());

        ui.widget(label(8, 8, be.getBlockState().getBlock().getName(), TITLE_COLOR));
        ui.widget(dynamicLabel(8, 28, () -> "Formed: " + enabledText(be.isFormed()), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 40, () -> "Tier: " + be.getTier(), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 52, () -> "Threads: %d / %d".formatted(be.getUsedThread(), be.getTotalThread()), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 64, () -> "Parallel: " + be.getParallelCount(), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 76, () -> "Bytes: %s / %s".formatted(
            Tooltips.ofBytes(be.getAvailableBytes()).getString(),
            Tooltips.ofBytes(be.getTotalBytes()).getString()
        ), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 88, () -> "Status: " + be.getPreviewStatusComponent().getString(), STATUS_COLOR));
        return ui;
    }

    public static ModularUI createStorageSystemUI(ECOStorageSystemBlockEntity be, Player player) {
        int width = 198;
        int height = 130;
        var ui = new ModularUI(width, height, be, player).background(panel());

        ui.widget(label(8, 8, be.getBlockState().getBlock().getName(), TITLE_COLOR));
        ui.widget(dynamicLabel(8, 28, () -> "Formed: " + enabledText(be.isFormed()), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 40, () -> "Tier: " + be.getTier(), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 52, () -> "Types: %s / %s".formatted(
            Tooltips.ofNumber(be.getTotalUsedTypes()).getString(),
            Tooltips.ofNumber(be.getTotalTypes()).getString()
        ), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 64, () -> "Bytes: %s / %s".formatted(
            Tooltips.ofBytes(be.getTotalUsedBytes()).getString(),
            Tooltips.ofBytes(be.getTotalBytes()).getString()
        ), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 76, () -> "Energy: %s / %s".formatted(
            Tooltips.ofNumber(be.getStoredEnergy()).getString(),
            Tooltips.ofNumber(be.getMaxEnergy()).getString()
        ), STATUS_COLOR));
        ui.widget(dynamicLabel(8, 88, () -> "Status: " + be.getPreviewStatusComponent().getString(), STATUS_COLOR));
        return ui;
    }

    private static String enabledText(boolean value) {
        return value ? "Enabled" : "Disabled";
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
