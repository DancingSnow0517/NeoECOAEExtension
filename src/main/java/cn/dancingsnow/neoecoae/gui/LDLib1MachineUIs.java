package cn.dancingsnow.neoecoae.gui;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Layout;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1BuilderPanel;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Textures;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Widgets;
import cn.dancingsnow.neoecoae.gui.ldlib1.MultiblockBuildUiAdapter;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public final class LDLib1MachineUIs {
    private LDLib1MachineUIs() {
    }

    public static ModularUI createIntegratedWorkingStationUI(ECOIntegratedWorkingStationBlockEntity be, Player player) {
        var ui = new ModularUI(NELDLib1Layout.DEFAULT_WIDTH, NELDLib1Layout.DEFAULT_HEIGHT_WITH_INV, be, player)
            .background(NELDLib1Textures.BACKGROUND);

        ui.widget(NELDLib1Widgets.title(8, 8, Component.translatable("block.neoecoae.integrated_working_station")));
        ui.widget(NELDLib1Widgets.label(8, 24, Component.translatable("gui.neoecoae.common.input")));
        NELDLib1Widgets.addInventoryGrid(ui, be.getInput().toContainer(), 9, 8, 38, 3, true, true);
        ui.widget(NELDLib1Widgets.label(72, 24, Component.translatable("gui.neoecoae.common.output")));
        ui.widget(NELDLib1Widgets.outputSlot(be.getOutput().toContainer(), 0, 72, 56));
        ui.widget(NELDLib1Widgets.label(104, 24, Component.translatable("gui.neoecoae.common.upgrades")));
        NELDLib1Widgets.addInventoryGrid(ui, be.getUpgrades().toContainer(), 4, 104, 38, 1, true, true);
        ui.widget(NELDLib1Widgets.verticalProgress(
            () -> Math.min(1.0, Math.max(0.0, be.getProcessingTime() / 200.0)),
            132,
            38,
            6,
            54,
            NELDLib1Textures.BAR
        ));
        ui.widget(NELDLib1Widgets.tank(be.getInputTank(), 148, 38, 18, 54));
        ui.widget(NELDLib1Widgets.tank(be.getOutputTank(), 170, 38, 18, 54));

        ui.widget(NELDLib1Widgets.label(8, 116, Component.translatable("container.inventory"), NELDLib1Widgets.TITLE_COLOR));
        NELDLib1Widgets.addPlayerInventory(ui, player, NELDLib1Layout.PLAYER_INV_X, 130);
        return ui;
    }

    public static ModularUI createCraftingControllerUI(ECOCraftingSystemBlockEntity be, Player player) {
        var ui = new ModularUI(NELDLib1Layout.DEFAULT_WIDTH, 332, be, player)
            .background(NELDLib1Textures.BACKGROUND);

        ui.widget(NELDLib1Widgets.title(8, 8, be.getBlockState().getBlock().getName()));
        ui.widget(NELDLib1Widgets.image(4, 22, 190, 82, NELDLib1Textures.CRAFTING_STATUS_BACKGROUND));
        ui.widget(NELDLib1Widgets.image(
            148,
            28,
            18,
            18,
            () -> be.isOverclocked() ? NELDLib1Textures.OVERCLOCK_ON : NELDLib1Textures.OVERCLOCK_OFF
        ));
        ui.widget(NELDLib1Widgets.image(
            170,
            28,
            18,
            18,
            () -> be.isActiveCooling() ? NELDLib1Textures.COOLING_ON : NELDLib1Textures.COOLING_OFF
        ));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 28, () -> line("gui.neoecoae.common.overclock", enabledText(be.isOverclocked()))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 40, () -> line("gui.neoecoae.common.active_cooling", enabledText(be.isActiveCooling()))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 52, () -> line("gui.neoecoae.common.threads", "%d / %d".formatted(
            be.getRunningThreadCount(),
            be.getAvailableThreads()
        ))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 64, () -> line("gui.neoecoae.common.status", be.getPreviewStatusComponent().getString())));
        ui.widget(NELDLib1Widgets.horizontalProgress(
            () -> Math.min(1.0, Math.max(0.0, be.getCoolant() / (double) ECOCraftingSystemBlockEntity.MAX_COOLANT)),
            8,
            74,
            86,
            6,
            NELDLib1Textures.PROGRESS_BAR_COOLANT
        ));
        ui.widget(NELDLib1Widgets.horizontalProgress(
            () -> Math.min(1.0, Math.max(0.0, be.getEffectiveOverclockTimes() / 9.0)),
            102,
            74,
            86,
            6,
            NELDLib1Textures.PROGRESS_BAR_LIMIT
        ));

        ui.widget(NELDLib1Widgets.button(8, 86, 52, 16, Component.translatable("gui.neoecoae.multiblock.preview"), data -> {
            if (!data.isRemote) {
                be.previewStructure(player);
            }
        }));
        ui.widget(NELDLib1Widgets.button(64, 86, 52, 16, Component.translatable("gui.neoecoae.multiblock.build"), data -> {
            if (!data.isRemote) {
                be.autoBuild(player);
            }
        }));
        ui.widget(NELDLib1Widgets.button(120, 86, 64, 16, Component.translatable("gui.neoecoae.crafting.clear_coolant"), data -> {
            if (!data.isRemote) {
                be.clearCoolant();
            }
        }));

        NELDLib1BuilderPanel.add(ui, player, craftingAdapter(be), 8, 110);

        ui.widget(NELDLib1Widgets.label(8, 226, Component.translatable("container.inventory"), NELDLib1Widgets.TITLE_COLOR));
        NELDLib1Widgets.addPlayerInventory(ui, player, NELDLib1Layout.PLAYER_INV_X, 240);
        return ui;
    }

    public static ModularUI createCraftingPatternBusUI(ECOCraftingPatternBusBlockEntity be, Player player) {
        var ui = new ModularUI(NELDLib1Layout.DEFAULT_WIDTH, NELDLib1Layout.DEFAULT_HEIGHT_WITH_INV, be, player)
            .background(NELDLib1Textures.BACKGROUND);

        ui.widget(NELDLib1Widgets.title(8, 8, Component.translatable("block.neoecoae.crafting_pattern_bus")));
        ui.widget(NELDLib1Widgets.label(8, 24, Component.translatable("gui.neoecoae.pattern_bus.patterns_page", 1, 36)));
        var patternInventory = be.getTerminalPatternInventory().toContainer();
        for (int slot = 0; slot < 36; slot++) {
            int col = slot % 9;
            int row = slot / 9;
            ui.widget(NELDLib1Widgets
                .machineSlot(patternInventory, slot, 8 + col * NELDLib1Layout.SLOT, 38 + row * NELDLib1Layout.SLOT, true, true)
                .setOverlay(NELDLib1Textures.PATTERN_OVERLAY));
        }

        ui.widget(NELDLib1Widgets.label(8, 116, Component.translatable("container.inventory"), NELDLib1Widgets.TITLE_COLOR));
        NELDLib1Widgets.addPlayerInventory(ui, player, NELDLib1Layout.PLAYER_INV_X, 130);
        return ui;
    }

    public static ModularUI createFluidHatchUI(ECOFluidInputHatchBlockEntity be, Player player, String titleKey) {
        return createFluidHatchUI(be, player, titleKey, be.tank);
    }

    public static ModularUI createFluidHatchUI(ECOFluidOutputHatchBlockEntity be, Player player, String titleKey) {
        return createFluidHatchUI(be, player, titleKey, be.tank);
    }

    private static ModularUI createFluidHatchUI(IUIHolder holder, Player player, String titleKey, FluidTank tank) {
        var ui = new ModularUI(NELDLib1Layout.DEFAULT_WIDTH, NELDLib1Layout.DEFAULT_HEIGHT_WITH_INV, holder, player)
            .background(NELDLib1Textures.BACKGROUND);

        ui.widget(NELDLib1Widgets.title(8, 8, Component.translatable(titleKey)));
        ui.widget(NELDLib1Widgets.tank(tank, 8, 30, 18, 54));
        ui.widget(NELDLib1Widgets.dynamicLabel(34, 32, () -> line("gui.neoecoae.common.fluid", tank.getFluid().getDisplayName().getString())));
        ui.widget(NELDLib1Widgets.dynamicLabel(34, 44, () -> line("gui.neoecoae.common.amount", "%s / %s".formatted(
            Tooltips.ofNumber(tank.getFluidAmount()).getString(),
            Tooltips.ofNumber(tank.getCapacity()).getString()
        ))));

        ui.widget(NELDLib1Widgets.label(8, 116, Component.translatable("container.inventory"), NELDLib1Widgets.TITLE_COLOR));
        NELDLib1Widgets.addPlayerInventory(ui, player, NELDLib1Layout.PLAYER_INV_X, 130);
        return ui;
    }

    public static ModularUI createComputationSystemUI(ECOComputationSystemBlockEntity be, Player player) {
        var ui = new ModularUI(198, 218, be, player).background(NELDLib1Textures.BACKGROUND);

        ui.widget(NELDLib1Widgets.title(8, 8, be.getBlockState().getBlock().getName()));
        ui.widget(NELDLib1Widgets.image(4, 22, 190, 74, NELDLib1Textures.CRAFTING_STATUS_BACKGROUND));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 28, () -> line("gui.neoecoae.common.formed", enabledText(be.isFormed()))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 40, () -> line("gui.neoecoae.common.tier", String.valueOf(be.getTier()))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 52, () -> line("gui.neoecoae.common.threads", "%d / %d".formatted(be.getUsedThread(), be.getTotalThread()))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 64, () -> line("gui.neoecoae.common.parallel", String.valueOf(be.getParallelCount()))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 76, () -> line("gui.neoecoae.common.bytes", "%s / %s".formatted(
            Tooltips.ofBytes(be.getAvailableBytes()).getString(),
            Tooltips.ofBytes(be.getTotalBytes()).getString()
        ))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 88, () -> line("gui.neoecoae.common.status", be.getPreviewStatusComponent().getString())));
        NELDLib1BuilderPanel.add(ui, player, computationAdapter(be), 8, 100);
        return ui;
    }

    public static ModularUI createStorageSystemUI(ECOStorageSystemBlockEntity be, Player player) {
        var ui = new ModularUI(198, 230, be, player).background(NELDLib1Textures.BACKGROUND);

        ui.widget(NELDLib1Widgets.title(8, 8, be.getBlockState().getBlock().getName()));
        ui.widget(NELDLib1Widgets.image(4, 22, 190, 74, NELDLib1Textures.CRAFTING_STATUS_BACKGROUND));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 28, () -> line("gui.neoecoae.common.formed", enabledText(be.isFormed()))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 40, () -> line("gui.neoecoae.common.tier", String.valueOf(be.getTier()))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 52, () -> line("gui.neoecoae.common.types", "%s / %s".formatted(
            Tooltips.ofNumber(be.getTotalUsedTypes()).getString(),
            Tooltips.ofNumber(be.getTotalTypes()).getString()
        ))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 64, () -> line("gui.neoecoae.common.bytes", "%s / %s".formatted(
            Tooltips.ofBytes(be.getTotalUsedBytes()).getString(),
            Tooltips.ofBytes(be.getTotalBytes()).getString()
        ))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 76, () -> line("gui.neoecoae.common.energy", "%s / %s".formatted(
            Tooltips.ofNumber(be.getStoredEnergy()).getString(),
            Tooltips.ofNumber(be.getMaxEnergy()).getString()
        ))));
        ui.widget(NELDLib1Widgets.dynamicLabel(8, 88, () -> line("gui.neoecoae.common.status", be.getPreviewStatusComponent().getString())));
        NELDLib1BuilderPanel.add(ui, player, storageAdapter(be), 8, 100);
        return ui;
    }

    private static MultiblockBuildUiAdapter craftingAdapter(ECOCraftingSystemBlockEntity be) {
        return new MultiblockBuildUiAdapter() {
            @Override
            public int getSelectedBuildLength() {
                return be.getSelectedBuildLength();
            }

            @Override
            public int getPreviewMissingBlocks() {
                return be.getPreviewMissingBlocks();
            }

            @Override
            public int getPreviewConflictBlocks() {
                return be.getPreviewConflictBlocks();
            }

            @Override
            public int getPreviewReusedBlocks() {
                return be.getPreviewReusedBlocks();
            }

            @Override
            public int getPreviewRequiredItems() {
                return be.getPreviewRequiredItems();
            }

            @Override
            public Component getPreviewStatusComponent() {
                return be.getPreviewStatusComponent();
            }

            @Override
            public void decreaseBuildLength() {
                be.decreaseBuildLength();
            }

            @Override
            public void increaseBuildLength() {
                be.increaseBuildLength();
            }

            @Override
            public void previewStructure(Player player) {
                be.previewStructure(player);
            }

            @Override
            public void autoBuild(Player player) {
                be.autoBuild(player);
            }

            @Override
            public boolean isBuildInProgress() {
                return be.isBuildInProgress();
            }

            @Override
            public boolean isFormed() {
                return be.isFormed();
            }
        };
    }

    private static MultiblockBuildUiAdapter computationAdapter(ECOComputationSystemBlockEntity be) {
        return new MultiblockBuildUiAdapter() {
            @Override
            public int getSelectedBuildLength() {
                return be.getSelectedBuildLength();
            }

            @Override
            public int getPreviewMissingBlocks() {
                return be.getPreviewMissingBlocks();
            }

            @Override
            public int getPreviewConflictBlocks() {
                return be.getPreviewConflictBlocks();
            }

            @Override
            public int getPreviewReusedBlocks() {
                return be.getPreviewReusedBlocks();
            }

            @Override
            public int getPreviewRequiredItems() {
                return be.getPreviewRequiredItems();
            }

            @Override
            public Component getPreviewStatusComponent() {
                return be.getPreviewStatusComponent();
            }

            @Override
            public void decreaseBuildLength() {
                be.decreaseBuildLength();
            }

            @Override
            public void increaseBuildLength() {
                be.increaseBuildLength();
            }

            @Override
            public void previewStructure(Player player) {
                be.previewStructure(player);
            }

            @Override
            public void autoBuild(Player player) {
                be.autoBuild(player);
            }

            @Override
            public boolean isBuildInProgress() {
                return be.isBuildInProgress();
            }

            @Override
            public boolean isFormed() {
                return be.isFormed();
            }
        };
    }

    private static MultiblockBuildUiAdapter storageAdapter(ECOStorageSystemBlockEntity be) {
        return new MultiblockBuildUiAdapter() {
            @Override
            public int getSelectedBuildLength() {
                return be.getSelectedBuildLength();
            }

            @Override
            public int getPreviewMissingBlocks() {
                return be.getPreviewMissingBlocks();
            }

            @Override
            public int getPreviewConflictBlocks() {
                return be.getPreviewConflictBlocks();
            }

            @Override
            public int getPreviewReusedBlocks() {
                return be.getPreviewReusedBlocks();
            }

            @Override
            public int getPreviewRequiredItems() {
                return be.getPreviewRequiredItems();
            }

            @Override
            public Component getPreviewStatusComponent() {
                return be.getPreviewStatusComponent();
            }

            @Override
            public void decreaseBuildLength() {
                be.decreaseBuildLength();
            }

            @Override
            public void increaseBuildLength() {
                be.increaseBuildLength();
            }

            @Override
            public void previewStructure(Player player) {
                be.previewStructure(player);
            }

            @Override
            public void autoBuild(Player player) {
                be.autoBuild(player);
            }

            @Override
            public boolean isBuildInProgress() {
                return be.isBuildInProgress();
            }

            @Override
            public boolean isFormed() {
                return be.isFormed();
            }
        };
    }

    private static String enabledText(boolean value) {
        return Component.translatable(value ? "gui.neoecoae.common.enabled" : "gui.neoecoae.common.disabled").getString();
    }

    private static String line(String labelKey, String value) {
        return Component.translatable(labelKey).getString() + ": " + value;
    }
}
