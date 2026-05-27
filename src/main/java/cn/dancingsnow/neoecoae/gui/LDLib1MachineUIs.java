package cn.dancingsnow.neoecoae.gui;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.api.IECOTier;
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
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1UiSpecs;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Widgets;
import cn.dancingsnow.neoecoae.gui.ldlib1.MultiblockBuildUiAdapter;
import cn.dancingsnow.neoecoae.gui.ldlib1.window.NEBuilderWindow;
import cn.dancingsnow.neoecoae.gui.ldlib1.window.NETerminalPanel;
import cn.dancingsnow.neoecoae.gui.ldlib1.window.NEToolBar;
import cn.dancingsnow.neoecoae.util.NETextFormat;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import static cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1UiSpecs.IntegratedWorkingStationSpec;
import static cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1UiSpecs.StorageControllerSpec;
import static cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1UiSpecs.ComputationControllerSpec;
import static cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1UiSpecs.CraftingControllerSpec;
import static cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1UiSpecs.PatternBusSpec;
import static cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1UiSpecs.FluidHatchSpec;

/**
 * LDLib1 machine UI factory. Each method recreates the LDLib2 (Taffy/StylesheetManager)
 * UI layout using fixed-coordinate LDLib1 widgets, with coordinates driven by
 * {@link NELDLib1UiSpecs}.
 *
 * <p>Business logic (storage, computation, crafting, etc.) is NOT modified.</p>
 */
public final class LDLib1MachineUIs {

    private LDLib1MachineUIs() {
    }

    // =========================================================================
    // Integrated Working Station
    // =========================================================================
    public static ModularUI createIntegratedWorkingStationUI(ECOIntegratedWorkingStationBlockEntity be, Player player) {
        var ui = new ModularUI(
            IntegratedWorkingStationSpec.WIDTH,
            IntegratedWorkingStationSpec.HEIGHT,
            be, player
        ).background(NELDLib1Textures.BACKGROUND);

        ui.widget(NELDLib1Widgets.title(
            IntegratedWorkingStationSpec.TITLE_X, IntegratedWorkingStationSpec.TITLE_Y,
            Component.translatable("block.neoecoae.integrated_working_station")
        ));

        int labelY = IntegratedWorkingStationSpec.INPUT_TANK_Y - 10;

        // Input fluid tank (left)
        ui.widget(NELDLib1Widgets.label(
            IntegratedWorkingStationSpec.INPUT_TANK_X, labelY,
            Component.translatable("gui.neoecoae.common.input")
        ));
        ui.widget(NELDLib1Widgets.tank(
            be.getInputTank(),
            IntegratedWorkingStationSpec.INPUT_TANK_X, IntegratedWorkingStationSpec.INPUT_TANK_Y,
            IntegratedWorkingStationSpec.TANK_W, IntegratedWorkingStationSpec.TANK_H
        ));

        // Input item grid (3×3) — slots are self-explanatory, no label needed here
        NELDLib1Widgets.addInventoryGrid(
            ui, be.getInput().toContainer(), 9,
            IntegratedWorkingStationSpec.INPUT_GRID_X, IntegratedWorkingStationSpec.INPUT_GRID_Y,
            IntegratedWorkingStationSpec.INPUT_GRID_COLS, true, true
        );

        // Output slot — self-explanatory
        ui.widget(NELDLib1Widgets.outputSlot(
            be.getOutput().toContainer(), 0,
            IntegratedWorkingStationSpec.OUTPUT_SLOT_X, IntegratedWorkingStationSpec.OUTPUT_SLOT_Y
        ));

        // Vertical progress bar — short label
        ui.widget(NELDLib1Widgets.label(
            IntegratedWorkingStationSpec.PROGRESS_X - 4, labelY,
            Component.translatable("gui.neoecoae.common.progress")
        ));
        ui.widget(NELDLib1Widgets.verticalProgress(
            () -> Math.min(1.0, Math.max(0.0, be.getProcessingTime() / 200.0)),
            IntegratedWorkingStationSpec.PROGRESS_X, IntegratedWorkingStationSpec.PROGRESS_Y,
            IntegratedWorkingStationSpec.PROGRESS_W, IntegratedWorkingStationSpec.PROGRESS_H,
            NELDLib1Textures.BAR
        ));

        // Output fluid tank (right)
        ui.widget(NELDLib1Widgets.label(
            IntegratedWorkingStationSpec.OUTPUT_TANK_X, labelY,
            Component.translatable("gui.neoecoae.common.output")
        ));
        ui.widget(NELDLib1Widgets.tank(
            be.getOutputTank(),
            IntegratedWorkingStationSpec.OUTPUT_TANK_X, IntegratedWorkingStationSpec.OUTPUT_TANK_Y,
            IntegratedWorkingStationSpec.TANK_W, IntegratedWorkingStationSpec.TANK_H
        ));

        // Upgrade slots (4 vertical, far right)
        ui.widget(NELDLib1Widgets.label(
            IntegratedWorkingStationSpec.UPGRADE_SLOTS_X, labelY,
            Component.translatable("gui.neoecoae.common.upgrades")
        ));
        NELDLib1Widgets.addInventoryGrid(
            ui, be.getUpgrades().toContainer(), 4,
            IntegratedWorkingStationSpec.UPGRADE_SLOTS_X, IntegratedWorkingStationSpec.UPGRADE_SLOTS_Y,
            1, true, true
        );

        // Player inventory
        ui.widget(NELDLib1Widgets.label(
            IntegratedWorkingStationSpec.PLAYER_INV_X, IntegratedWorkingStationSpec.PLAYER_INV_Y - 14,
            Component.translatable("container.inventory"), NELDLib1Widgets.TITLE_COLOR
        ));
        NELDLib1Widgets.addPlayerInventory(ui, player,
            IntegratedWorkingStationSpec.PLAYER_INV_X, IntegratedWorkingStationSpec.PLAYER_INV_Y);

        return ui;
    }

    // =========================================================================
    // Storage Controller — using window toolkit components
    // =========================================================================
    public static ModularUI createStorageSystemUI(ECOStorageSystemBlockEntity be, Player player) {
        var ui = new ModularUI(
            StorageControllerSpec.WIDTH, StorageControllerSpec.HEIGHT,
            be, player
        ); // transparent root — no .background()

        // ── 1. Builder floating window (hidden by default, added LAST to float on top) ──
        NEBuilderWindow[] builderHolder = new NEBuilderWindow[1];
        builderHolder[0] = new NEBuilderWindow(
            StorageControllerSpec.BUILDER_FLOAT_X, StorageControllerSpec.BUILDER_FLOAT_Y,
            storageAdapter(be), player,
            () -> builderHolder[0].hide()
        );
        var builderWindow = builderHolder[0];
        builderWindow.hide();
        // Allow dragging within the root UI bounds
        builderWindow.setDragBounds(0, 0,
            StorageControllerSpec.WIDTH - StorageControllerSpec.BUILDER_FLOAT_W,
            StorageControllerSpec.HEIGHT - StorageControllerSpec.BUILDER_FLOAT_H);

        // ── 2. Left tool bar ──
        var toolBar = new NEToolBar(
            StorageControllerSpec.HAMMER_BTN_X, StorageControllerSpec.HAMMER_BTN_Y,
            20, 2
        );
        // Hammer icon rendered as text symbol; tooltip shows "Builder"
        toolBar.addButton("\u2692", // ⚒ hammer-and-pick symbol
            Component.translatable("gui.neoecoae.common.show_builder"),
            data -> builderWindow.toggle());

        // ── 3. Main status terminal panel ──
        var terminal = new NETerminalPanel(
            StorageControllerSpec.MAIN_FRAME_X, StorageControllerSpec.MAIN_FRAME_Y,
            StorageControllerSpec.MAIN_FRAME_W, StorageControllerSpec.MAIN_FRAME_H,
            shortTitle("gui.neoecoae.ui.storage_system.short", be.getTier()),
            11
        );
        terminal.addStatusLine("gui.neoecoae.common.formed",
            () -> enabledText(be.isFormed()));
        terminal.addStatusLine("gui.neoecoae.common.tier",
            () -> String.valueOf(be.getTier()));
        terminal.addStatusLine("gui.neoecoae.common.types",
            () -> "%s / %s".formatted(
                Tooltips.ofNumber(be.getTotalUsedTypes()).getString(),
                Tooltips.ofNumber(be.getTotalTypes()).getString()));
        terminal.addStatusLine("gui.neoecoae.common.bytes",
            () -> "%s / %s".formatted(
                NETextFormat.formatBytes(be.getTotalUsedBytes()),
                NETextFormat.formatBytes(be.getTotalBytes())));
        terminal.addStatusLine("gui.neoecoae.common.energy",
            () -> "%s / %s".formatted(
                Tooltips.ofNumber(be.getStoredEnergy()).getString(),
                Tooltips.ofNumber(be.getMaxEnergy()).getString()));
        terminal.addStatusLine("gui.neoecoae.common.status",
            () -> be.getPreviewStatusComponent().getString());

        // ── Add in order: toolbar → terminal → builder (builder on top!) ──
        ui.widget(toolBar);
        ui.widget(terminal);
        ui.widget(builderWindow);

        return ui;
    }

    // =========================================================================
    // Computation Controller — using window toolkit components
    // =========================================================================
    public static ModularUI createComputationSystemUI(ECOComputationSystemBlockEntity be, Player player) {
        var ui = new ModularUI(
            ComputationControllerSpec.WIDTH, ComputationControllerSpec.HEIGHT,
            be, player
        ); // transparent root — no .background()

        // ── 1. Builder floating window (hidden by default, added LAST to float on top) ──
        NEBuilderWindow[] builderHolder = new NEBuilderWindow[1];
        builderHolder[0] = new NEBuilderWindow(
            ComputationControllerSpec.BUILDER_FLOAT_X, ComputationControllerSpec.BUILDER_FLOAT_Y,
            computationAdapter(be), player,
            () -> builderHolder[0].hide()
        );
        var builderWindow = builderHolder[0];
        builderWindow.hide();
        builderWindow.setDragBounds(0, 0,
            ComputationControllerSpec.WIDTH - ComputationControllerSpec.BUILDER_FLOAT_W,
            ComputationControllerSpec.HEIGHT - ComputationControllerSpec.BUILDER_FLOAT_H);

        // ── 2. Left tool bar ──
        var toolBar = new NEToolBar(
            ComputationControllerSpec.HAMMER_BTN_X, ComputationControllerSpec.HAMMER_BTN_Y,
            20, 2
        );
        toolBar.addButton("\u2692", // ⚒ hammer-and-pick symbol
            Component.translatable("gui.neoecoae.common.show_builder"),
            data -> builderWindow.toggle());

        // ── 3. Main status terminal panel ──
        var terminal = new NETerminalPanel(
            ComputationControllerSpec.MAIN_FRAME_X, ComputationControllerSpec.MAIN_FRAME_Y,
            ComputationControllerSpec.MAIN_FRAME_W, ComputationControllerSpec.MAIN_FRAME_H,
            shortTitle("gui.neoecoae.ui.computation_system.short", be.getTier()),
            11
        );
        terminal.addStatusLine("gui.neoecoae.common.formed",
            () -> enabledText(be.isFormed()));
        terminal.addStatusLine("gui.neoecoae.common.tier",
            () -> String.valueOf(be.getTier()));
        terminal.addStatusLine("gui.neoecoae.common.threads",
            () -> "%d / %d".formatted(be.getUsedThread(), be.getTotalThread()));
        terminal.addStatusLine("gui.neoecoae.common.parallel",
            () -> String.valueOf(be.getParallelCount()));
        terminal.addStatusLine("gui.neoecoae.common.bytes",
            () -> "%s / %s".formatted(
                NETextFormat.formatBytes(be.getAvailableBytes()),
                NETextFormat.formatBytes(be.getTotalBytes())));
        terminal.addStatusLine("gui.neoecoae.common.status",
            () -> be.getPreviewStatusComponent().getString());

        // ── Add in order: toolbar → terminal → builder (builder on top!) ──
        ui.widget(toolBar);
        ui.widget(terminal);
        ui.widget(builderWindow);

        return ui;
    }

    // =========================================================================
    // Crafting Controller
    // =========================================================================
    public static ModularUI createCraftingControllerUI(ECOCraftingSystemBlockEntity be, Player player) {
        var ui = new ModularUI(
            CraftingControllerSpec.WIDTH, CraftingControllerSpec.HEIGHT,
            be, player
        ).background(NELDLib1Textures.BACKGROUND);

        // Short title
        ui.widget(NELDLib1Widgets.title(
            CraftingControllerSpec.TITLE_X, CraftingControllerSpec.TITLE_Y,
            shortTitle("gui.neoecoae.ui.crafting_controller.short", be.getTier())
        ));

        // Status panel background (DARK)
        ui.widget(NELDLib1Widgets.image(
            CraftingControllerSpec.STATUS_PANEL_X, CraftingControllerSpec.STATUS_PANEL_Y,
            CraftingControllerSpec.STATUS_PANEL_W, CraftingControllerSpec.STATUS_PANEL_H,
            NELDLib1Textures.CRAFTING_BACKGROUND_DARK
        ));

        // Toggle icons
        ui.widget(NELDLib1Widgets.image(
            CraftingControllerSpec.OVERCLOCK_ICON_X, CraftingControllerSpec.OVERCLOCK_ICON_Y,
            CraftingControllerSpec.TOGGLE_ICON_SIZE, CraftingControllerSpec.TOGGLE_ICON_SIZE,
            () -> be.isOverclocked() ? NELDLib1Textures.OVERCLOCK_ON : NELDLib1Textures.OVERCLOCK_OFF
        ));
        ui.widget(NELDLib1Widgets.image(
            CraftingControllerSpec.COOLING_ICON_X, CraftingControllerSpec.COOLING_ICON_Y,
            CraftingControllerSpec.TOGGLE_ICON_SIZE, CraftingControllerSpec.TOGGLE_ICON_SIZE,
            () -> be.isActiveCooling() ? NELDLib1Textures.COOLING_ON : NELDLib1Textures.COOLING_OFF
        ));

        // Status rows — LIGHT text on dark background
        int rowY = CraftingControllerSpec.STATUS_ROW_START_Y;
        int sx = CraftingControllerSpec.STATUS_ROW_START_X;
        int sp = CraftingControllerSpec.STATUS_ROW_SPACING;

        ui.widget(NELDLib1Widgets.dynamicLabelLight(sx, rowY,
            () -> formatLine("gui.neoecoae.common.overclock", enabledText(be.isOverclocked()))));
        rowY += sp;
        ui.widget(NELDLib1Widgets.dynamicLabelLight(sx, rowY,
            () -> formatLine("gui.neoecoae.common.active_cooling", enabledText(be.isActiveCooling()))));
        rowY += sp;
        ui.widget(NELDLib1Widgets.dynamicLabelLight(sx, rowY,
            () -> formatLine("gui.neoecoae.common.threads", "%d / %d".formatted(
                be.getRunningThreadCount(), be.getAvailableThreads()))));
        rowY += sp;
        ui.widget(NELDLib1Widgets.dynamicLabelLight(sx, rowY,
            () -> formatLine("gui.neoecoae.common.status", be.getPreviewStatusComponent().getString())));

        // Progress bars
        ui.widget(NELDLib1Widgets.horizontalProgress(
            () -> Math.min(1.0, Math.max(0.0, be.getCoolant() / (double) ECOCraftingSystemBlockEntity.MAX_COOLANT)),
            CraftingControllerSpec.COOLANT_PROGRESS_X, CraftingControllerSpec.COOLANT_PROGRESS_Y,
            CraftingControllerSpec.COOLANT_PROGRESS_W, CraftingControllerSpec.COOLANT_PROGRESS_H,
            NELDLib1Textures.PROGRESS_BAR_COOLANT
        ));
        ui.widget(NELDLib1Widgets.horizontalProgress(
            () -> Math.min(1.0, Math.max(0.0, be.getEffectiveOverclockTimes() / 9.0)),
            CraftingControllerSpec.LIMIT_PROGRESS_X, CraftingControllerSpec.LIMIT_PROGRESS_Y,
            CraftingControllerSpec.LIMIT_PROGRESS_W, CraftingControllerSpec.LIMIT_PROGRESS_H,
            NELDLib1Textures.PROGRESS_BAR_LIMIT
        ));

        // Action buttons
        ui.widget(NELDLib1Widgets.button(
            CraftingControllerSpec.PREVIEW_BUTTON_X, CraftingControllerSpec.PREVIEW_BUTTON_Y,
            CraftingControllerSpec.ACTION_BUTTON_W, CraftingControllerSpec.ACTION_BUTTON_H,
            Component.translatable("gui.neoecoae.multiblock.preview"),
            data -> { if (!data.isRemote) be.previewStructure(player); }
        ));
        ui.widget(NELDLib1Widgets.button(
            CraftingControllerSpec.BUILD_BUTTON_X, CraftingControllerSpec.BUILD_BUTTON_Y,
            CraftingControllerSpec.ACTION_BUTTON_W, CraftingControllerSpec.ACTION_BUTTON_H,
            Component.translatable("gui.neoecoae.multiblock.build"),
            data -> { if (!data.isRemote) be.autoBuild(player); }
        ));
        ui.widget(NELDLib1Widgets.button(
            CraftingControllerSpec.CLEAR_BUTTON_X, CraftingControllerSpec.CLEAR_BUTTON_Y,
            64, CraftingControllerSpec.ACTION_BUTTON_H,
            Component.translatable("gui.neoecoae.crafting.clear_coolant"),
            data -> { if (!data.isRemote) be.clearCoolant(); }
        ));

        // Builder panel
        NELDLib1BuilderPanel.add(
            ui, player, craftingAdapter(be),
            CraftingControllerSpec.BUILDER_PANEL_X, CraftingControllerSpec.BUILDER_PANEL_Y,
            CraftingControllerSpec.BUILDER_PANEL_W, CraftingControllerSpec.BUILDER_PANEL_H
        );

        // Player inventory
        ui.widget(NELDLib1Widgets.label(
            CraftingControllerSpec.PLAYER_INV_X, CraftingControllerSpec.PLAYER_INV_Y - 14,
            Component.translatable("container.inventory"), NELDLib1Widgets.TITLE_COLOR
        ));
        NELDLib1Widgets.addPlayerInventory(ui, player,
            CraftingControllerSpec.PLAYER_INV_X, CraftingControllerSpec.PLAYER_INV_Y);

        return ui;
    }

    // =========================================================================
    // Smart Pattern Bus
    // =========================================================================
    public static ModularUI createCraftingPatternBusUI(ECOCraftingPatternBusBlockEntity be, Player player) {
        var ui = new ModularUI(PatternBusSpec.WIDTH, PatternBusSpec.HEIGHT, be, player)
            .background(NELDLib1Textures.BACKGROUND);

        ui.widget(NELDLib1Widgets.title(
            PatternBusSpec.TITLE_X, PatternBusSpec.TITLE_Y,
            Component.translatable("block.neoecoae.crafting_pattern_bus")
        ));

        // Show all available slots, guarded against inventory size
        var patternInventory = be.getTerminalPatternInventory().toContainer();
        int maxSlots = PatternBusSpec.PATTERN_GRID_COLS * PatternBusSpec.PATTERN_GRID_ROWS;
        int inventorySize = patternInventory.getContainerSize();
        int visibleSlots = Math.min(maxSlots, inventorySize);

        for (int slot = 0; slot < visibleSlots; slot++) {
            int col = slot % PatternBusSpec.PATTERN_GRID_COLS;
            int row = slot / PatternBusSpec.PATTERN_GRID_COLS;
            ui.widget(NELDLib1Widgets.patternSlot(
                patternInventory, slot,
                PatternBusSpec.PATTERN_GRID_X + col * NELDLib1Layout.SLOT,
                PatternBusSpec.PATTERN_GRID_Y + row * NELDLib1Layout.SLOT,
                true, true
            ));
        }

        // Player inventory
        ui.widget(NELDLib1Widgets.label(
            PatternBusSpec.PLAYER_INV_X, PatternBusSpec.PLAYER_INV_Y - 14,
            Component.translatable("container.inventory"), NELDLib1Widgets.TITLE_COLOR
        ));
        NELDLib1Widgets.addPlayerInventory(ui, player,
            PatternBusSpec.PLAYER_INV_X, PatternBusSpec.PLAYER_INV_Y);

        return ui;
    }

    // =========================================================================
    // Fluid Input/Output Hatch
    // =========================================================================
    public static ModularUI createFluidHatchUI(ECOFluidInputHatchBlockEntity be, Player player, String titleKey) {
        return createFluidHatchUI(be, player, titleKey, be.tank);
    }

    public static ModularUI createFluidHatchUI(ECOFluidOutputHatchBlockEntity be, Player player, String titleKey) {
        return createFluidHatchUI(be, player, titleKey, be.tank);
    }

    private static ModularUI createFluidHatchUI(IUIHolder holder, Player player, String titleKey, FluidTank tank) {
        var ui = new ModularUI(FluidHatchSpec.WIDTH, FluidHatchSpec.HEIGHT, holder, player)
            .background(NELDLib1Textures.BACKGROUND);

        ui.widget(NELDLib1Widgets.title(
            FluidHatchSpec.TITLE_X, FluidHatchSpec.TITLE_Y,
            Component.translatable(titleKey)
        ));

        // Tank centered with background
        ui.widget(NELDLib1Widgets.tank(
            tank,
            FluidHatchSpec.TANK_X, FluidHatchSpec.TANK_Y,
            FluidHatchSpec.TANK_W, FluidHatchSpec.TANK_H
        ));

        // Fluid info
        ui.widget(NELDLib1Widgets.dynamicLabel(
            FluidHatchSpec.FLUID_NAME_X, FluidHatchSpec.FLUID_NAME_Y,
            () -> formatLine("gui.neoecoae.common.fluid", tank.getFluid().getDisplayName().getString())
        ));
        ui.widget(NELDLib1Widgets.dynamicLabel(
            FluidHatchSpec.AMOUNT_LABEL_X, FluidHatchSpec.AMOUNT_LABEL_Y,
            () -> formatLine("gui.neoecoae.common.amount", "%s / %s mB".formatted(
                Tooltips.ofNumber(tank.getFluidAmount()).getString(),
                Tooltips.ofNumber(tank.getCapacity()).getString()
            ))
        ));

        // Player inventory
        ui.widget(NELDLib1Widgets.label(
            FluidHatchSpec.PLAYER_INV_X, FluidHatchSpec.PLAYER_INV_Y - 14,
            Component.translatable("container.inventory"), NELDLib1Widgets.TITLE_COLOR
        ));
        NELDLib1Widgets.addPlayerInventory(ui, player,
            FluidHatchSpec.PLAYER_INV_X, FluidHatchSpec.PLAYER_INV_Y);

        return ui;
    }

    // =========================================================================
    // Adapter factories (bridge block entity → MultiblockBuildUiAdapter)
    // =========================================================================

    private static MultiblockBuildUiAdapter storageAdapter(ECOStorageSystemBlockEntity be) {
        return new MultiblockBuildUiAdapter() {
            @Override public int getSelectedBuildLength() { return be.getSelectedBuildLength(); }
            @Override public int getPreviewMissingBlocks() { return be.getPreviewMissingBlocks(); }
            @Override public int getPreviewConflictBlocks() { return be.getPreviewConflictBlocks(); }
            @Override public int getPreviewReusedBlocks() { return be.getPreviewReusedBlocks(); }
            @Override public int getPreviewRequiredItems() { return be.getPreviewRequiredItems(); }
            @Override public Component getPreviewStatusComponent() { return be.getPreviewStatusComponent(); }
            @Override public void decreaseBuildLength() { be.decreaseBuildLength(); }
            @Override public void increaseBuildLength() { be.increaseBuildLength(); }
            @Override public void previewStructure(Player player) { be.previewStructure(player); }
            @Override public void autoBuild(Player player) { be.autoBuild(player); }
            @Override public boolean isBuildInProgress() { return be.isBuildInProgress(); }
            @Override public boolean isFormed() { return be.isFormed(); }
        };
    }

    private static MultiblockBuildUiAdapter computationAdapter(ECOComputationSystemBlockEntity be) {
        return new MultiblockBuildUiAdapter() {
            @Override public int getSelectedBuildLength() { return be.getSelectedBuildLength(); }
            @Override public int getPreviewMissingBlocks() { return be.getPreviewMissingBlocks(); }
            @Override public int getPreviewConflictBlocks() { return be.getPreviewConflictBlocks(); }
            @Override public int getPreviewReusedBlocks() { return be.getPreviewReusedBlocks(); }
            @Override public int getPreviewRequiredItems() { return be.getPreviewRequiredItems(); }
            @Override public Component getPreviewStatusComponent() { return be.getPreviewStatusComponent(); }
            @Override public void decreaseBuildLength() { be.decreaseBuildLength(); }
            @Override public void increaseBuildLength() { be.increaseBuildLength(); }
            @Override public void previewStructure(Player player) { be.previewStructure(player); }
            @Override public void autoBuild(Player player) { be.autoBuild(player); }
            @Override public boolean isBuildInProgress() { return be.isBuildInProgress(); }
            @Override public boolean isFormed() { return be.isFormed(); }
        };
    }

    private static MultiblockBuildUiAdapter craftingAdapter(ECOCraftingSystemBlockEntity be) {
        return new MultiblockBuildUiAdapter() {
            @Override public int getSelectedBuildLength() { return be.getSelectedBuildLength(); }
            @Override public int getPreviewMissingBlocks() { return be.getPreviewMissingBlocks(); }
            @Override public int getPreviewConflictBlocks() { return be.getPreviewConflictBlocks(); }
            @Override public int getPreviewReusedBlocks() { return be.getPreviewReusedBlocks(); }
            @Override public int getPreviewRequiredItems() { return be.getPreviewRequiredItems(); }
            @Override public Component getPreviewStatusComponent() { return be.getPreviewStatusComponent(); }
            @Override public void decreaseBuildLength() { be.decreaseBuildLength(); }
            @Override public void increaseBuildLength() { be.increaseBuildLength(); }
            @Override public void previewStructure(Player player) { be.previewStructure(player); }
            @Override public void autoBuild(Player player) { be.autoBuild(player); }
            @Override public boolean isBuildInProgress() { return be.isBuildInProgress(); }
            @Override public boolean isFormed() { return be.isFormed(); }
        };
    }

    // =========================================================================
    // Formatting helpers
    // =========================================================================

    /**
     * Build a short controller title using a translatable key with a tier format arg.
     * Example: shortTitle("gui.neoecoae.ui.storage_system.short", be.getTier())
     * produces "ECO - L9 Storage System" (en) / "ECO - L9 存储系统" (zh_cn).
     */
    private static Component shortTitle(String key, IECOTier tier) {
        return Component.translatable(key, tier.toString());
    }

    private static String enabledText(boolean value) {
        return Component.translatable(
            value ? "gui.neoecoae.common.enabled" : "gui.neoecoae.common.disabled"
        ).getString();
    }

    private static String formatLine(String labelKey, String value) {
        return Component.translatable(labelKey).getString() + ": " + value;
    }
}
