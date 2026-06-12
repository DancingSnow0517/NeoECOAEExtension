package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingModuleCell;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class NECraftingControllerWidget extends NELDLibSyncedStateWidget<NECraftingUiState> {
    public static final int UI_WIDTH = 344;
    public static final int UI_HEIGHT = 294;
    private static final long ENERGY_GAUGE_REFERENCE = 1_000_000_000L;
    private static final int HEADER_STATUS_LABEL_COLOR = 0xFF5D5D5D;
    private static final int HEADER_STATUS_SUCCESS_COLOR = 0xFF00A850;
    private static final int HEADER_STATUS_ERROR_COLOR = 0xFFC03434;
    private static final int HEADER_STATUS_MUTED_COLOR = 0xFF606060;

    private static final ResourceLocation MODULE_CORE_SIDE =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/block/crafting/core/core_side.png");
    private static final ResourceLocation MODULE_PARALLEL_CORE_FRONT =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/block/crafting/core/parallel_core_north.png");
    private static final ResourceLocation MODULE_PARALLEL_CORE_LIGHT_L4 =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/block/crafting/core/parallel_core_light_a.png");
    private static final ResourceLocation MODULE_PARALLEL_CORE_LIGHT_L6 =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/block/crafting/core/parallel_core_light_b.png");
    private static final ResourceLocation MODULE_PARALLEL_CORE_LIGHT_L9 =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/block/crafting/core/parallel_core_light_c.png");

    private static final int PANEL_MARGIN = 7;
    private static final int MAIN_PANEL_X = PANEL_MARGIN;
    private static final int MAIN_PANEL_Y = 24;
    private static final int MAIN_PANEL_W = UI_WIDTH - PANEL_MARGIN * 2;
    private static final int MAIN_PANEL_H = 171;
    private static final int TOOLBAR_BUTTON_SIZE = 14;
    private static final int TOOLBAR_BUTTON_STRIDE = TOOLBAR_BUTTON_SIZE + 3;
    private static final int TOOLBAR_X = UI_WIDTH - PANEL_MARGIN - TOOLBAR_BUTTON_SIZE * 3 - 3 * 2;
    private static final int TOOLBAR_Y = 8;

    private static final int MODULE_AREA_X = MAIN_PANEL_X + 7;
    private static final int MODULE_AREA_Y = MAIN_PANEL_Y + 7;
    private static final int MODULE_AREA_W = MAIN_PANEL_W - 14;
    private static final int MODULE_AREA_H = 78;
    private static final int MODULE_GRID_X = MODULE_AREA_X + 6;
    private static final int MODULE_GRID_Y = MODULE_AREA_Y + 16;
    private static final int MODULE_GRID_W = MODULE_AREA_W - 12;
    private static final int MODULE_GRID_H = MODULE_AREA_H - 20;
    private static final int MIDDLE_AREA_Y = MODULE_AREA_Y + MODULE_AREA_H + 7;
    private static final int STATUS_AREA_X = MODULE_AREA_X;
    private static final int STATUS_AREA_Y = MIDDLE_AREA_Y;
    private static final int STATUS_AREA_W = 74;
    private static final int STATUS_AREA_H = 72;
    private static final int STATS_AREA_X = STATUS_AREA_X + STATUS_AREA_W + 6;
    private static final int STATS_AREA_Y = MIDDLE_AREA_Y;
    private static final int STATS_AREA_W = 154;
    private static final int STATS_AREA_H = 72;
    private static final int GAUGE_AREA_X = STATS_AREA_X + STATS_AREA_W + 6;
    private static final int GAUGE_AREA_Y = MIDDLE_AREA_Y;
    private static final int GAUGE_AREA_W = MODULE_AREA_X + MODULE_AREA_W - GAUGE_AREA_X;
    private static final int GAUGE_AREA_H = 72;
    private static final int GAUGE_BAR_Y = GAUGE_AREA_Y + 19;
    private static final int GAUGE_BAR_H = 32;
    private static final int GAUGE_BAR_W = 23;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INV_X = MODULE_AREA_X;
    private static final int PLAYER_INV_LABEL_Y = MAIN_PANEL_Y + 165 + 8;
    private static final int PLAYER_INV_Y = PLAYER_INV_LABEL_Y + 12;
    private static final int PLAYER_HOTBAR_Y = PLAYER_INV_Y + SLOT_SIZE * 3 + 4;
    private static final int TASK_PANEL_GAP = 8;
    private static final int TASK_PANEL_X = PLAYER_INV_X + SLOT_SIZE * 9 + TASK_PANEL_GAP;
    private static final int TASK_PANEL_Y = PLAYER_INV_LABEL_Y - 2;
    private static final int TASK_PANEL_W = UI_WIDTH - TASK_PANEL_X - PANEL_MARGIN;
    private static final int TASK_PANEL_H = PLAYER_HOTBAR_Y + SLOT_SIZE - TASK_PANEL_Y;
    private static final int TASK_CARD_X = TASK_PANEL_X + 8;
    private static final int TASK_CARD_Y = TASK_PANEL_Y + 19;
    private static final int TASK_CARD_W = TASK_PANEL_W - 16;
    private static final int TASK_CARD_H = 16;
    private static final int TASK_CARD_STRIDE = 18;
    private static final int TASK_LIST_BOTTOM_Y = TASK_PANEL_Y + TASK_PANEL_H - 1;
    private static final int TASK_SCROLLBAR_W = 3;
    private static final long TASK_FADE_MS = 360L;
    private static final long TASK_MOVE_MS = 140L;

    private final ECOCraftingSystemBlockEntity crafting;
    private final Inventory playerInventory;
    private final Map<String, TaskCardAnimation> taskAnimations = new LinkedHashMap<>();
    private int taskScrollOffset;
    private int lastTaskScrollOffset;

    public NECraftingControllerWidget(ECOCraftingSystemBlockEntity crafting, Player player) {
        super(
                crafting.getBlockState().getBlock().getName(),
                UI_WIDTH,
                UI_HEIGHT,
                NECraftingUiState.empty(crafting.getBlockPos()),
                crafting::createCraftingUiState,
                NELDLibStateCodecs::writeCrafting,
                NELDLibStateCodecs::readCrafting,
                10);
        this.crafting = crafting;
        this.playerInventory = player.getInventory();
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addToolbarButton(0, click -> {
            if (!click.isRemote) {
                crafting.toggleOverclocked();
                syncStateNow();
            }
        });
        addToolbarButton(1, click -> {
            if (!click.isRemote) {
                crafting.toggleActiveCooling();
                syncStateNow();
            }
        });
        addToolbarButton(2, click -> {
            if (!click.isRemote) {
                crafting.toggleAutoClearCoolingWaste();
                syncStateNow();
            }
        });
        addPlayerInventorySlots();
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int ox = getPositionX();
        int oy = getPositionY();
        NECraftingUiState state = currentState();

        NELDLibStyle.drawDarkInsetRect(graphics, ox + MAIN_PANEL_X, oy + MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);
        NELDLibStyle.drawDarkInsetRect(graphics, ox + MODULE_AREA_X, oy + MODULE_AREA_Y, MODULE_AREA_W, MODULE_AREA_H);
        NELDLibStyle.drawDarkInsetRect(graphics, ox + STATUS_AREA_X, oy + STATUS_AREA_Y, STATUS_AREA_W, STATUS_AREA_H);
        NELDLibStyle.drawDarkInsetRect(graphics, ox + STATS_AREA_X, oy + STATS_AREA_Y, STATS_AREA_W, STATS_AREA_H);
        NELDLibStyle.drawDarkInsetRect(graphics, ox + GAUGE_AREA_X, oy + GAUGE_AREA_Y, GAUGE_AREA_W, GAUGE_AREA_H);
        drawThreadUsageBar(
                graphics,
                absX(STATS_AREA_X + 8),
                absY(STATS_AREA_Y + 31),
                STATS_AREA_W - 16,
                9,
                state.occupiedRecipeSlots(),
                state.maxRecipeSlots());
        drawGaugeArea(graphics, state);
        drawPlayerInventorySlots(graphics);
        NELDLibStyle.drawDarkInsetRect(graphics, ox + TASK_PANEL_X, oy + TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NECraftingUiState state = currentState();
        drawLocalString(graphics, title, 8, 8, TEXT_PRIMARY);
        drawHeaderMachineStatus(graphics, state);
        drawToolbarIcons(graphics, state);
        drawModuleLabels(graphics, state);
        drawStatusArea(graphics, state);
        drawStatsArea(graphics, state);
        drawGaugeLabels(graphics);
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.common.inventory"),
                PLAYER_INV_X,
                PLAYER_INV_LABEL_Y,
                TEXT_MUTED);
        drawTaskPanel(graphics, state);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (renderToolbarTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (renderModuleTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (renderGaugeTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (renderTaskTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        renderStatsTooltip(graphics, mouseX, mouseY);
    }

    private void addToolbarButton(
            int index, java.util.function.Consumer<com.lowdragmc.lowdraglib.gui.util.ClickData> action) {
        addWidget(new ButtonWidget(
                TOOLBAR_X + index * TOOLBAR_BUTTON_STRIDE,
                TOOLBAR_Y,
                TOOLBAR_BUTTON_SIZE,
                TOOLBAR_BUTTON_SIZE,
                NELDLibStyle.aeToolbarButton(),
                action));
    }

    private void addPlayerInventorySlots() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addWidget(new SlotWidget(
                                playerInventory,
                                col + row * 9 + 9,
                                PLAYER_INV_X + col * SLOT_SIZE,
                                PLAYER_INV_Y + row * SLOT_SIZE,
                                true,
                                true)
                        .setBackgroundTexture(IGuiTexture.EMPTY)
                        .setLocationInfo(true, false));
            }
        }
        for (int col = 0; col < 9; col++) {
            addWidget(new SlotWidget(playerInventory, col, PLAYER_INV_X + col * SLOT_SIZE, PLAYER_HOTBAR_Y, true, true)
                    .setBackgroundTexture(IGuiTexture.EMPTY)
                    .setLocationInfo(true, true));
        }
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        int total = currentState().recipeEntries().size();
        int visible = visibleTaskCardCount();
        if (isMouseIn(TASK_PANEL_X, TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H, (int) mouseX, (int) mouseY)
                && total > visible) {
            taskScrollOffset = clampTaskScrollOffset(taskScrollOffset + (wheelDelta < 0 ? 1 : -1), total);
            return true;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    private void drawModuleLabels(GuiGraphics g, NECraftingUiState state) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.module_preview"),
                MODULE_AREA_X + 8,
                MODULE_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        NELDLibStyle.drawRight(
                g,
                font(),
                Component.literal("FT " + NELDLibText.number(state.parallelCount()) + "   FX "
                        + NELDLibText.number(state.workerCount())),
                absX(MODULE_AREA_X + MODULE_AREA_W - 8),
                absY(MODULE_AREA_Y + 5),
                NELDLibStyle.DARK_TEXT_VALUE);
        if (crafting.getBuildDefinition() == null) {
            NELDLibStyle.drawCentered(
                    g,
                    font(),
                    Component.translatable("emi.neoecoae.multiblock.empty_scene"),
                    absX(MODULE_AREA_X),
                    absY(MODULE_AREA_Y + 39),
                    MODULE_AREA_W,
                    NELDLibStyle.DARK_TEXT_MUTED);
            return;
        }
        drawModulePlane(g, state);
    }

    private void drawModulePlane(GuiGraphics g, NECraftingUiState state) {
        ModuleGrid grid = moduleGrid(state);
        if (grid.columns() <= 0) {
            NELDLibStyle.drawCentered(
                    g,
                    font(),
                    Component.translatable("gui.neoecoae.crafting.no_worker_cores"),
                    absX(MODULE_AREA_X),
                    absY(MODULE_AREA_Y + 39),
                    MODULE_AREA_W,
                    NELDLibStyle.DARK_TEXT_MUTED);
            return;
        }

        for (int col = 0; col < grid.columns(); col++) {
            int x = grid.x() + col * grid.cellSize();
            drawModuleCell(
                    g,
                    x,
                    grid.rowY(NECraftingModuleCell.Row.UPPER_PARALLEL),
                    grid.cellSize(),
                    moduleCellAt(state, col, NECraftingModuleCell.Row.UPPER_PARALLEL),
                    NECraftingModuleCell.Row.UPPER_PARALLEL);
            drawModuleCell(
                    g,
                    x,
                    grid.rowY(NECraftingModuleCell.Row.WORKER),
                    grid.cellSize(),
                    moduleCellAt(state, col, NECraftingModuleCell.Row.WORKER),
                    NECraftingModuleCell.Row.WORKER);
            drawModuleCell(
                    g,
                    x,
                    grid.rowY(NECraftingModuleCell.Row.LOWER_PARALLEL),
                    grid.cellSize(),
                    moduleCellAt(state, col, NECraftingModuleCell.Row.LOWER_PARALLEL),
                    NECraftingModuleCell.Row.LOWER_PARALLEL);
        }
    }

    private void drawModuleCell(
            GuiGraphics g, int x, int y, int size, NECraftingModuleCell cell, NECraftingModuleCell.Row row) {
        boolean active = cell != null;
        ResourceLocation baseTexture =
                active ? row == NECraftingModuleCell.Row.WORKER ? MODULE_CORE_SIDE : MODULE_PARALLEL_CORE_FRONT : null;
        ResourceLocation overlayTexture =
                active && row != NECraftingModuleCell.Row.WORKER ? lightForTier(cell.tier()) : null;
        drawTexturedModuleSlot(g, absX(x), absY(y), size, baseTexture, overlayTexture, active);
    }

    private void drawTexturedModuleSlot(
            GuiGraphics g,
            int x,
            int y,
            int size,
            ResourceLocation baseTexture,
            ResourceLocation overlayTexture,
            boolean active) {
        if (size >= 10) {
            NELDLibStyle.drawDarkInsetRect(g, x, y, size, size);
        } else {
            g.fill(x, y, x + size, y + size, 0xFF1B1822);
        }

        int pad = size >= 10 ? 2 : 1;
        int innerX = x + pad;
        int innerY = y + pad;
        int innerSize = Math.max(1, size - pad * 2);
        g.fill(innerX, innerY, innerX + innerSize, innerY + innerSize, 0xAA17141E);
        if (baseTexture != null) {
            g.blit(baseTexture, innerX, innerY, innerSize, innerSize, 0, 0, 16, 16, 16, 16);
        }
        if (overlayTexture != null) {
            g.blit(overlayTexture, innerX, innerY, innerSize, innerSize, 0, 0, 16, 16, 16, 16);
        }
        if (!active) {
            g.fill(innerX + 1, innerY + 1, innerX + innerSize - 1, innerY + innerSize - 1, 0x66000000);
        }
    }

    private boolean renderModuleTooltip(GuiGraphics g, int mouseX, int mouseY) {
        NECraftingUiState state = currentState();
        ModuleGrid grid = moduleGrid(state);
        if (grid.columns() <= 0) {
            return false;
        }

        int localX = mouseX - absX(grid.x());
        if (localX < 0 || localX >= grid.columns() * grid.cellSize()) {
            return false;
        }
        int column = localX / grid.cellSize();
        for (NECraftingModuleCell.Row row : NECraftingModuleCell.Row.values()) {
            int rowY = absY(grid.rowY(row));
            if (mouseY < rowY || mouseY >= rowY + grid.cellSize()) {
                continue;
            }
            if (row == NECraftingModuleCell.Row.WORKER) {
                renderWorkerTooltip(g, state, column, mouseX, mouseY);
            } else {
                renderParallelCoreTooltip(g, state, column, row, mouseX, mouseY);
            }
            return true;
        }
        return false;
    }

    private void renderWorkerTooltip(GuiGraphics g, NECraftingUiState state, int column, int mouseX, int mouseY) {
        ItemStack output = column >= 0 && column < state.workerCraftOutputs().size()
                ? state.workerCraftOutputs().get(column)
                : ItemStack.EMPTY;
        if (!output.isEmpty()) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), output));
            lines.add(Component.literal(formatModulePos(moduleCellAt(state, column, NECraftingModuleCell.Row.WORKER))));
            g.renderTooltip(font(), lines, output.getTooltipImage(), output, mouseX, mouseY);
            return;
        }
        g.renderComponentTooltip(
                font(),
                List.of(
                        Component.translatable("block.neoecoae.crafting_worker"),
                        Component.literal(
                                formatModulePos(moduleCellAt(state, column, NECraftingModuleCell.Row.WORKER)))),
                mouseX,
                mouseY);
    }

    private void renderParallelCoreTooltip(
            GuiGraphics g, NECraftingUiState state, int column, NECraftingModuleCell.Row row, int mouseX, int mouseY) {
        NECraftingModuleCell cell = moduleCellAt(state, column, row);
        if (cell == null) {
            g.renderComponentTooltip(
                    font(), List.of(Component.translatable("gui.neoecoae.crafting.no_parallel_core")), mouseX, mouseY);
            return;
        }
        g.renderComponentTooltip(
                font(),
                List.of(
                        Component.translatable(parallelCoreNameKey(cell.tier())),
                        Component.translatable(
                                "gui.neoecoae.crafting.parallel_per_core",
                                NELDLibText.number(parallelPerCore(cell.tier(), state.overclocked()))),
                        Component.literal(formatModulePos(cell))),
                mouseX,
                mouseY);
    }

    private void drawStatusArea(GuiGraphics g, NECraftingUiState state) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.status"),
                STATUS_AREA_X + 8,
                STATUS_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        int y = STATUS_AREA_Y + 21;
        drawStatusRow(
                g,
                Component.translatable("gui.neoecoae.crafting.overclock"),
                state.overclocked(),
                STATUS_AREA_X + 7,
                y);
        y += 15;
        drawStatusRow(
                g,
                Component.translatable("gui.neoecoae.crafting.cooling_short"),
                state.activeCooling(),
                STATUS_AREA_X + 7,
                y);
        y += 15;
        drawStatusRow(
                g,
                Component.translatable("gui.neoecoae.crafting.waste_short"),
                state.autoClearCoolingWaste(),
                STATUS_AREA_X + 7,
                y);
    }

    private void drawStatsArea(GuiGraphics g, NECraftingUiState state) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.stats"),
                STATS_AREA_X + 8,
                STATS_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        int x = STATS_AREA_X + 8;
        int y = STATS_AREA_Y + 19;
        drawCompactPairLine(
                g,
                Component.translatable("gui.neoecoae.crafting.recipe_slots").getString() + ": ",
                state.occupiedRecipeSlots(),
                state.maxRecipeSlots(),
                x,
                y);
        y += 25;
        drawInlineValueLine(
                g,
                Component.translatable("gui.neoecoae.crafting.batch_parallel").getString() + ": ",
                state.batchParallel(),
                x,
                y);
        y += 11;
        drawInlineValueLine(
                g,
                Component.translatable("gui.neoecoae.crafting.patterns_short").getString() + ": ",
                state.patternBusCount(),
                x,
                y);
        drawInlineValueLine(
                g,
                Component.translatable("gui.neoecoae.crafting.ft_cores_short").getString() + ": ",
                state.parallelCount(),
                x + 76,
                y);
    }

    private void drawGaugeArea(GuiGraphics g, NECraftingUiState state) {
        int gaugeY = GAUGE_BAR_Y;
        int gaugeH = GAUGE_BAR_H;
        int gaugeW = GAUGE_BAR_W;
        int energyX = GAUGE_AREA_X + 8;
        int coolantX = GAUGE_AREA_X + GAUGE_AREA_W - 8 - gaugeW;
        double energyRatio = clampRatio(state.energyUsage(), ENERGY_GAUGE_REFERENCE);
        double coolantRatio = clampRatio(state.coolantAmount(), state.coolantCapacity());

        drawVerticalReserveGauge(
                g, absX(energyX), absY(gaugeY), gaugeW, gaugeH, energyGaugeColor(energyRatio), energyRatio);
        drawVerticalReserveGauge(
                g, absX(coolantX), absY(gaugeY), gaugeW, gaugeH, NELDLibStyle.DARK_TEXT_BLUE, coolantRatio);
    }

    private void drawGaugeLabels(GuiGraphics g) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.energy_cooling"),
                GAUGE_AREA_X + 8,
                GAUGE_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        int gaugeY = GAUGE_BAR_Y;
        int gaugeH = GAUGE_BAR_H;
        int gaugeW = GAUGE_BAR_W;
        int energyX = GAUGE_AREA_X + 8;
        int coolantX = GAUGE_AREA_X + GAUGE_AREA_W - 8 - gaugeW;
        NELDLibStyle.drawCentered(
                g,
                font(),
                Component.translatable("gui.neoecoae.crafting.energy_short"),
                absX(energyX - 8),
                absY(gaugeY + gaugeH + 1),
                gaugeW + 16,
                NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibStyle.drawCentered(
                g,
                font(),
                Component.translatable("gui.neoecoae.crafting.cooling_short"),
                absX(coolantX - 8),
                absY(gaugeY + gaugeH + 1),
                gaugeW + 16,
                NELDLibStyle.DARK_TEXT_MUTED);
    }

    private void drawToolbarIcons(GuiGraphics graphics, NECraftingUiState state) {
        drawToolbarIcon(graphics, 0, state.overclocked() ? Icon.LEVEL_ENERGY : Icon.POWER_UNIT_AE);
        drawToolbarIcon(
                graphics,
                1,
                state.activeCooling() ? Icon.FLUID_SUBSTITUTION_ENABLED : Icon.FLUID_SUBSTITUTION_DISABLED);
        drawToolbarIcon(
                graphics, 2, state.autoClearCoolingWaste() ? Icon.CONDENSER_OUTPUT_TRASH : Icon.BACKGROUND_TRASH);
    }

    private void drawToolbarIcon(GuiGraphics graphics, int index, Icon icon) {
        NELDLibAe2StyleRenderer.drawAeIcon(
                graphics,
                icon,
                absX(TOOLBAR_X + index * TOOLBAR_BUTTON_STRIDE + (TOOLBAR_BUTTON_SIZE - icon.width) / 2),
                absY(TOOLBAR_Y + (TOOLBAR_BUTTON_SIZE - icon.height) / 2));
    }

    private void drawStatusRow(GuiGraphics g, Component label, boolean enabled, int x, int y) {
        int absX = absX(x);
        int absY = absY(y);
        NELDLibStyle.drawDarkInsetRect(g, absX, absY - 3, 13, 13);
        int light = enabled ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR;
        g.fill(absX + 4, absY + 1, absX + 9, absY + 6, light);
        g.drawString(font(), label, absX + 18, absY, NELDLibStyle.DARK_TEXT_MUTED, false);
        g.drawString(
                font(),
                Component.translatable(enabled ? "gui.neoecoae.common.on" : "gui.neoecoae.common.off"),
                absX + 44,
                absY,
                enabled ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR,
                false);
    }

    private void drawHeaderMachineStatus(GuiGraphics g, NECraftingUiState state) {
        Component formedLabel =
                Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component formedValue = boolText(state.formed());
        Component activeLabel = Component.literal("  ")
                .append(Component.translatable("gui.neoecoae.machine.active"))
                .append(": ");
        Component activeValue = boolText(state.active());
        int textW = font().width(formedLabel)
                + font().width(formedValue)
                + font().width(activeLabel)
                + font().width(activeValue);
        int titleRight = 8 + font().width(title) + 10;
        int rightLimit = TOOLBAR_X - 8;
        int textX = absX(Math.min(titleRight, Math.max(8, rightLimit - textW)));
        int textY = absY(8);
        g.drawString(font(), formedLabel, textX, textY, HEADER_STATUS_LABEL_COLOR, false);
        textX += font().width(formedLabel);
        g.drawString(
                font(),
                formedValue,
                textX,
                textY,
                state.formed() ? HEADER_STATUS_SUCCESS_COLOR : HEADER_STATUS_ERROR_COLOR,
                false);
        textX += font().width(formedValue);
        g.drawString(font(), activeLabel, textX, textY, HEADER_STATUS_LABEL_COLOR, false);
        textX += font().width(activeLabel);
        g.drawString(
                font(),
                activeValue,
                textX,
                textY,
                state.active() ? HEADER_STATUS_SUCCESS_COLOR : HEADER_STATUS_MUTED_COLOR,
                false);
    }

    private void drawPlayerInventorySlots(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                NELDLibAe2StyleRenderer.drawAeSlot(
                        graphics, absX(PLAYER_INV_X + col * SLOT_SIZE), absY(PLAYER_INV_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            NELDLibAe2StyleRenderer.drawAeSlot(graphics, absX(PLAYER_INV_X + col * SLOT_SIZE), absY(PLAYER_HOTBAR_Y));
        }
    }

    private void drawTaskPanel(GuiGraphics g, NECraftingUiState state) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.tasks"),
                TASK_PANEL_X + 8,
                TASK_PANEL_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        NELDLibStyle.drawRight(
                g,
                font(),
                Component.literal(NELDLibText.number(state.recipeEntries().size())),
                absX(TASK_PANEL_X + TASK_PANEL_W - 8),
                absY(TASK_PANEL_Y + 5),
                NELDLibStyle.DARK_TEXT_VALUE);

        taskScrollOffset =
                clampTaskScrollOffset(taskScrollOffset, state.recipeEntries().size());
        List<TaskCardAnimation> cards = updateTaskAnimations(state);
        if (cards.isEmpty() && state.recipeEntries().isEmpty()) {
            NELDLibStyle.drawCentered(
                    g,
                    font(),
                    Component.translatable("gui.neoecoae.crafting.no_tasks"),
                    absX(TASK_PANEL_X + 6),
                    absY(TASK_PANEL_Y + 42),
                    TASK_PANEL_W - 12,
                    NELDLibStyle.DARK_TEXT_MUTED);
            return;
        }

        for (TaskCardAnimation card : cards) {
            drawTaskCard(g, card);
        }
        drawTaskScrollbar(g, state.recipeEntries().size(), visibleTaskCardCount());
    }

    private List<TaskCardAnimation> updateTaskAnimations(NECraftingUiState state) {
        long now = Util.getMillis();
        Set<String> activeKeys = new HashSet<>();
        int total = state.recipeEntries().size();
        int visible = Math.min(visibleTaskCardCount(), Math.max(0, total - taskScrollOffset));
        boolean scrolled = taskScrollOffset != lastTaskScrollOffset;
        lastTaskScrollOffset = taskScrollOffset;

        for (int i = 0; i < visible; i++) {
            int entryIndex = taskScrollOffset + i;
            NECraftingRecipeUiEntry entry = state.recipeEntries().get(entryIndex);
            String key = taskEntryKey(entry, entryIndex);
            activeKeys.add(key);
            int targetY = TASK_CARD_Y + i * TASK_CARD_STRIDE;
            TaskCardAnimation animation = taskAnimations.get(key);
            if (animation == null) {
                animation = new TaskCardAnimation(entry, targetY, scrolled);
                taskAnimations.put(key, animation);
            }
            animation.entry = entry;
            animation.targetY = targetY;
            animation.exiting = false;
            if (scrolled) {
                animation.snapTo(targetY);
            }
        }

        Iterator<Map.Entry<String, TaskCardAnimation>> iterator =
                taskAnimations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TaskCardAnimation> entry = iterator.next();
            TaskCardAnimation animation = entry.getValue();
            if (!activeKeys.contains(entry.getKey()) && !animation.exiting) {
                if (scrolled) {
                    iterator.remove();
                    continue;
                }
                animation.exiting = true;
                animation.exitStartedMs = now;
            }
            animation.update(now);
            if (animation.exiting && animation.alpha <= 0.02F) {
                iterator.remove();
            }
        }

        List<TaskCardAnimation> cards = new ArrayList<>(taskAnimations.values());
        cards.sort(Comparator.comparingDouble(card -> card.y));
        return cards;
    }

    private void drawTaskCard(GuiGraphics g, TaskCardAnimation card) {
        int y = Math.round(card.y);
        if (y + TASK_CARD_H < TASK_CARD_Y || y > TASK_LIST_BOTTOM_Y) {
            return;
        }
        float alpha = Mth.clamp(card.alpha, 0.0F, 1.0F);
        NECraftingRecipeUiEntry entry = card.entry;
        int x = TASK_CARD_X;
        int absX = absX(x);
        int absY = absY(y);

        drawTaskCardRect(g, absX, absY, TASK_CARD_W, TASK_CARD_H, alpha, taskStatusColor(entry.status()));
        if (alpha > 0.22F && !entry.output().isEmpty()) {
            g.renderItem(entry.output(), absX + 1, absY);
        }

        int textX = x + 20;
        int textY = y + 4;
        String amountText = "x" + formatTaskAmount(entry.outputAmount());
        int amountW = font().width(amountText);
        int maxNameW = Math.max(16, TASK_CARD_W - 28 - amountW);
        String name = fitText(entry.output().getHoverName().getString(), maxNameW);
        g.drawString(font(), name, absX(textX), absY(textY), withAlpha(NELDLibStyle.DARK_TEXT_PRIMARY, alpha), false);
        NELDLibStyle.drawRight(
                g,
                font(),
                Component.literal(amountText),
                absX(TASK_CARD_X + TASK_CARD_W - 5),
                absY(textY),
                withAlpha(NELDLibStyle.DARK_TEXT_VALUE, alpha));
        drawTaskProgressBar(g, absX + 20, absY + TASK_CARD_H - 4, TASK_CARD_W - 25, 2, entry, alpha);
    }

    private void drawTaskScrollbar(GuiGraphics g, int total, int visible) {
        if (total <= visible) {
            return;
        }
        int trackX = absX(TASK_PANEL_X + TASK_PANEL_W - 5);
        int trackY = absY(TASK_CARD_Y);
        int trackH = Math.max(1, TASK_LIST_BOTTOM_Y - TASK_CARD_Y - 1);
        int thumbH = Math.max(10, trackH * visible / Math.max(1, total));
        int maxScroll = Math.max(1, total - visible);
        int thumbY = trackY + (trackH - thumbH) * taskScrollOffset / maxScroll;
        g.fill(trackX, trackY, trackX + TASK_SCROLLBAR_W, trackY + trackH, 0xAA17141E);
        g.fill(trackX, thumbY, trackX + TASK_SCROLLBAR_W, thumbY + thumbH, 0xFF8B83A0);
    }

    private void drawTaskCardRect(GuiGraphics g, int x, int y, int w, int h, float alpha, int accentColor) {
        g.fill(x, y, x + w, y + h, withAlpha(0xFFD8D3E4, alpha));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, withAlpha(0xFF121016, alpha));
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, withAlpha(0xFF4D4855, alpha));
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, withAlpha(0xFF2C2735, alpha));
        g.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, withAlpha(accentColor, alpha));
    }

    private void drawTaskProgressBar(
            GuiGraphics g, int x, int y, int w, int h, NECraftingRecipeUiEntry entry, float alpha) {
        g.fill(x, y, x + w, y + h, withAlpha(0xAA17141E, alpha));
        int fillW = ratioWidth(Math.max(0L, entry.totalTicks() - entry.remainingTicks()), entry.totalTicks(), w);
        if (entry.status() == NECraftingRecipeUiEntry.Status.WAITING_OUTPUT) {
            fillW = w;
        } else if (entry.status() == NECraftingRecipeUiEntry.Status.QUEUED) {
            fillW = 1;
        }
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + h, withAlpha(taskStatusColor(entry.status()), alpha));
        }
    }

    private boolean renderTaskTooltip(GuiGraphics g, int mouseX, int mouseY) {
        taskScrollOffset = clampTaskScrollOffset(
                taskScrollOffset, currentState().recipeEntries().size());
        for (TaskCardAnimation card : taskAnimations.values()) {
            if (card.alpha < 0.35F || card.exiting) {
                continue;
            }
            int y = Math.round(card.y);
            if (!isMouseIn(TASK_CARD_X, y, TASK_CARD_W, TASK_CARD_H, mouseX, mouseY)) {
                continue;
            }
            NECraftingRecipeUiEntry entry = card.entry;
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.output()));
            lines.add(Component.translatable(taskStatusKey(entry.status())));
            lines.add(Component.translatable(
                    "gui.neoecoae.crafting.task.amount", formatTaskAmount(entry.outputAmount())));
            lines.add(Component.translatable(
                    "gui.neoecoae.crafting.task.crafts", NELDLibText.number(entry.craftCount())));
            if (entry.totalTicks() > 0L) {
                long done = Math.max(0L, entry.totalTicks() - entry.remainingTicks());
                lines.add(Component.translatable(
                        "gui.neoecoae.crafting.task.time", formatTaskTime(done), formatTaskTime(entry.totalTicks())));
            }
            g.renderTooltip(font(), lines, entry.output().getTooltipImage(), entry.output(), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private int visibleTaskCardCount() {
        int space = TASK_LIST_BOTTOM_Y - TASK_CARD_Y;
        if (space < TASK_CARD_H) {
            return 1;
        }
        return Math.max(1, 1 + (space - TASK_CARD_H) / TASK_CARD_STRIDE);
    }

    private int clampTaskScrollOffset(int value, int total) {
        return Mth.clamp(value, 0, Math.max(0, total - visibleTaskCardCount()));
    }

    private void drawVerticalReserveGauge(
            GuiGraphics g, int x, int y, int w, int h, int accentColor, double fillRatio) {
        NELDLibStyle.drawDarkInsetRect(g, x, y, w, h);
        int ix = x + 7;
        int iy = y + 7;
        int iw = w - 14;
        int ih = h - 14;
        int fillH = (int) Math.round(ih * Math.max(0.0D, Math.min(1.0D, fillRatio)));
        int fillY = iy + ih - fillH;
        g.fill(ix, iy, ix + iw, iy + ih, 0xAA17141E);
        if (fillH > 0) {
            g.fill(ix, fillY, ix + iw, iy + ih, accentColor);
            g.fill(ix, fillY, ix + iw, Math.min(fillY + 2, iy + ih), 0x70FFFFFF);
        }
    }

    private void drawThreadUsageBar(GuiGraphics g, int x, int y, int w, int h, long current, long max) {
        NELDLibStyle.drawDarkInsetRect(g, x, y, w, h);
        int ix = x + 3;
        int iy = y + 3;
        int iw = Math.max(0, w - 6);
        int ih = Math.max(0, h - 6);
        int fillW = ratioWidth(current, max, iw);
        if (iw <= 0 || ih <= 0) {
            return;
        }
        g.fill(ix, iy, ix + iw, iy + ih, 0xAA17141E);
        if (fillW > 0) {
            g.fill(ix, iy, ix + fillW, iy + ih, NELDLibStyle.DARK_TEXT_SUCCESS);
        }
    }

    private boolean renderToolbarTooltip(GuiGraphics g, int mouseX, int mouseY) {
        NECraftingUiState state = currentState();
        if (isMouseIn(TOOLBAR_X, TOOLBAR_Y, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE, mouseX, mouseY)) {
            g.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable(
                            state.overclocked()
                                    ? "gui.neoecoae.crafting.overclock.on"
                                    : "gui.neoecoae.crafting.overclock.off")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(
                TOOLBAR_X + TOOLBAR_BUTTON_STRIDE,
                TOOLBAR_Y,
                TOOLBAR_BUTTON_SIZE,
                TOOLBAR_BUTTON_SIZE,
                mouseX,
                mouseY)) {
            g.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable(
                            state.activeCooling()
                                    ? "gui.neoecoae.crafting.active_cooling.on"
                                    : "gui.neoecoae.crafting.active_cooling.off")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(
                TOOLBAR_X + TOOLBAR_BUTTON_STRIDE * 2,
                TOOLBAR_Y,
                TOOLBAR_BUTTON_SIZE,
                TOOLBAR_BUTTON_SIZE,
                mouseX,
                mouseY)) {
            g.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable(
                            state.autoClearCoolingWaste()
                                    ? "gui.neoecoae.crafting.auto_clear_coolant.on"
                                    : "gui.neoecoae.crafting.auto_clear_coolant.off")),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    private boolean renderGaugeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        NECraftingUiState state = currentState();
        int gaugeY = GAUGE_BAR_Y;
        int gaugeH = GAUGE_BAR_H;
        int gaugeW = GAUGE_BAR_W;
        int energyX = GAUGE_AREA_X + 8;
        int coolantX = GAUGE_AREA_X + GAUGE_AREA_W - 8 - gaugeW;
        if (isMouseIn(energyX, gaugeY, gaugeW, gaugeH, mouseX, mouseY)) {
            g.renderTooltip(
                    font(),
                    List.of(
                            Component.translatable("gui.neoecoae.crafting.energy_usage"),
                            Component.literal(NELDLibText.number(state.energyUsage()) + " AE/t")),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(coolantX, gaugeY, gaugeW, gaugeH, mouseX, mouseY)) {
            g.renderTooltip(
                    font(),
                    List.of(
                            Component.translatable("gui.neoecoae.crafting.coolant"),
                            Component.literal(
                                    NELDLibText.usedTotal(state.coolantAmount(), state.coolantCapacity()) + " mB"),
                            Component.literal(NELDLibText.percentOrNA(state.coolantAmount(), state.coolantCapacity()))),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    private void renderStatsTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (!isMouseIn(STATS_AREA_X, STATS_AREA_Y, STATS_AREA_W, STATS_AREA_H, mouseX, mouseY)) {
            return;
        }
        NECraftingUiState state = currentState();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.neoecoae.crafting.parallel_core_tiers"));
        lines.add(Component.literal("FT4: " + countTier(state, 1) + " x " + parallelPerCore(1, state.overclocked())));
        lines.add(Component.literal("FT6: " + countTier(state, 2) + " x " + parallelPerCore(2, state.overclocked())));
        lines.add(Component.literal("FT9: " + countTier(state, 3) + " x " + parallelPerCore(3, state.overclocked())));
        lines.add(Component.translatable("gui.neoecoae.crafting.recipe_slots")
                .append(": ")
                .append(Component.literal(NELDLibText.usedTotal(state.occupiedRecipeSlots(), state.maxRecipeSlots()))));
        lines.add(Component.translatable("gui.neoecoae.crafting.batch_parallel")
                .append(": ")
                .append(Component.literal(NELDLibText.number(state.batchParallel()))));
        g.renderTooltip(font(), lines, Optional.empty(), mouseX, mouseY);
    }

    private void drawLine(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font(), text, absX(x), absY(y), color, false);
    }

    private void drawInlineValueLine(GuiGraphics g, String label, long value, int x, int y) {
        int cursor = NELDLibStyle.drawSegment(g, font(), label, absX(x), absY(y), NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibStyle.drawSegment(
                g, font(), NELDLibText.number(value), absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_VALUE);
    }

    private void drawCompactPairLine(GuiGraphics g, String label, long current, long max, int x, int y) {
        int cursor = NELDLibStyle.drawSegment(g, font(), label, absX(x), absY(y), NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibStyle.drawSegment(
                g, font(), NELDLibText.number(current), absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_SUCCESS);
        cursor += NELDLibStyle.drawSegment(g, font(), " / ", absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibStyle.drawSegment(
                g, font(), NELDLibText.number(max), absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_VALUE);
    }

    private static int countTier(NECraftingUiState state, int tier) {
        int count = 0;
        for (int value : state.parallelCoreTiers()) {
            if (value == tier) {
                count++;
            }
        }
        return count;
    }

    private static int parallelPerCore(int tier, boolean overclocked) {
        return switch (tier) {
            case 3 -> overclocked ? 384 : 256;
            case 2 -> overclocked ? 96 : 72;
            default -> overclocked ? 32 : 24;
        };
    }

    private static String taskEntryKey(NECraftingRecipeUiEntry entry, int index) {
        return entry.id() == null || entry.id().isBlank() ? "task:" + index : entry.id();
    }

    private static int taskStatusColor(NECraftingRecipeUiEntry.Status status) {
        return switch (status) {
            case RUNNING -> NELDLibStyle.DARK_TEXT_SUCCESS;
            case QUEUED -> NELDLibStyle.DARK_TEXT_WARNING;
            case WAITING_OUTPUT -> NELDLibStyle.DARK_TEXT_BLUE;
        };
    }

    private static String taskStatusKey(NECraftingRecipeUiEntry.Status status) {
        return switch (status) {
            case RUNNING -> "gui.neoecoae.crafting.task.status.running";
            case QUEUED -> "gui.neoecoae.crafting.task.status.queued";
            case WAITING_OUTPUT -> "gui.neoecoae.crafting.task.status.waiting_output";
        };
    }

    private String fitText(String text, int maxWidth) {
        if (font().width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        return font().plainSubstrByWidth(text, Math.max(1, maxWidth - font().width(suffix))) + suffix;
    }

    private static String formatTaskAmount(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) {
            return Long.toString(safe);
        }
        if (safe < 1_000_000L) {
            return compactDecimal(safe, 1_000L, "K");
        }
        if (safe < 1_000_000_000L) {
            return compactDecimal(safe, 1_000_000L, "M");
        }
        if (safe < 1_000_000_000_000L) {
            return compactDecimal(safe, 1_000_000_000L, "G");
        }
        return compactDecimal(safe, 1_000_000_000_000L, "T");
    }

    private static String compactDecimal(long value, long unit, String suffix) {
        double scaled = (double) value / (double) unit;
        if (scaled >= 100.0D || Math.abs(scaled - Math.rint(scaled)) < 0.05D) {
            return String.format(Locale.US, "%.0f%s", scaled, suffix);
        }
        return String.format(Locale.US, "%.1f%s", scaled, suffix);
    }

    private static String formatTaskTime(long ticks) {
        long safe = Math.max(0L, ticks);
        if (safe < 20L) {
            return safe + "t";
        }
        double seconds = safe / 20.0D;
        if (seconds < 60.0D) {
            return String.format(Locale.US, "%.1fs", seconds);
        }
        long wholeSeconds = Math.round(seconds);
        return (wholeSeconds / 60L) + "m " + (wholeSeconds % 60L) + "s";
    }

    private static int withAlpha(int color, float alpha) {
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        int baseAlpha = (color >>> 24) & 0xFF;
        int outAlpha = Mth.clamp(Math.round(baseAlpha * clamped), 0, 255);
        return (outAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int ratioWidth(long current, long max, int fullWidth) {
        if (fullWidth <= 0 || max <= 0 || current <= 0) {
            return 0;
        }
        long clamped = Math.max(0L, Math.min(current, max));
        return (int) Math.max(1L, Math.min(fullWidth, clamped * fullWidth / max));
    }

    private static double clampRatio(long value, long max) {
        if (value <= 0 || max <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (double) value / (double) max));
    }

    private static int energyGaugeColor(double ratio) {
        if (ratio >= 0.9D) {
            return NELDLibStyle.DARK_TEXT_ERROR;
        }
        if (ratio >= 0.5D) {
            return NELDLibStyle.DARK_TEXT_WARNING;
        }
        return NELDLibStyle.DARK_TEXT_SUCCESS;
    }

    private static ResourceLocation lightForTier(int tier) {
        return switch (tier) {
            case 3 -> MODULE_PARALLEL_CORE_LIGHT_L9;
            case 2 -> MODULE_PARALLEL_CORE_LIGHT_L6;
            default -> MODULE_PARALLEL_CORE_LIGHT_L4;
        };
    }

    private static String parallelCoreNameKey(int tier) {
        return switch (tier) {
            case 3 -> "block.neoecoae.crafting_parallel_core_l9";
            case 2 -> "block.neoecoae.crafting_parallel_core_l6";
            default -> "block.neoecoae.crafting_parallel_core_l4";
        };
    }

    private static NECraftingModuleCell moduleCellAt(
            NECraftingUiState state, int column, NECraftingModuleCell.Row row) {
        for (NECraftingModuleCell cell : state.moduleCells()) {
            if (cell.column() == column && cell.row() == row) {
                return cell;
            }
        }
        return null;
    }

    private static String formatModulePos(NECraftingModuleCell cell) {
        if (cell == null || cell.pos() == null) {
            return "";
        }
        BlockPos pos = cell.pos();
        return "x=" + pos.getX() + ", y=" + pos.getY() + ", z=" + pos.getZ();
    }

    private ModuleGrid moduleGrid(NECraftingUiState state) {
        int maxColumn = -1;
        for (NECraftingModuleCell cell : state.moduleCells()) {
            maxColumn = Math.max(maxColumn, cell.column());
        }
        int columns = Math.max(maxColumn + 1, state.workerCount());
        if (columns <= 0) {
            return new ModuleGrid(MODULE_GRID_X, MODULE_GRID_Y, 0, 18);
        }

        int cellSize = Math.min(18, Math.max(6, Math.min(MODULE_GRID_W / columns, MODULE_GRID_H / 3)));
        int totalW = columns * cellSize;
        int x = MODULE_GRID_X + Math.max(0, (MODULE_GRID_W - totalW) / 2);
        int y = MODULE_GRID_Y + Math.max(0, (MODULE_GRID_H - cellSize * 3) / 2);
        return new ModuleGrid(x, y, columns, cellSize);
    }

    private record ModuleGrid(int x, int y, int columns, int cellSize) {
        int rowY(NECraftingModuleCell.Row row) {
            return y
                    + switch (row) {
                        case UPPER_PARALLEL -> 0;
                        case WORKER -> cellSize;
                        case LOWER_PARALLEL -> cellSize * 2;
                    };
        }
    }

    private static final class TaskCardAnimation {
        private NECraftingRecipeUiEntry entry;
        private float y;
        private int targetY;
        private float alpha;
        private long lastUpdateMs;
        private boolean exiting;
        private long exitStartedMs;

        private TaskCardAnimation(NECraftingRecipeUiEntry entry, int targetY, boolean immediate) {
            this.entry = entry;
            this.targetY = targetY;
            this.lastUpdateMs = Util.getMillis();
            if (immediate) {
                snapTo(targetY);
            } else {
                this.y = targetY + 5.0F;
                this.alpha = 0.0F;
            }
        }

        private void snapTo(int targetY) {
            this.y = targetY;
            this.targetY = targetY;
            this.alpha = 1.0F;
            this.lastUpdateMs = Util.getMillis();
        }

        private void update(long nowMs) {
            long elapsed = Math.max(0L, Math.min(1000L, nowMs - lastUpdateMs));
            lastUpdateMs = nowMs;
            float moveT = TASK_MOVE_MS <= 0L ? 1.0F : Mth.clamp((float) elapsed / (float) TASK_MOVE_MS, 0.0F, 1.0F);
            y += (targetY - y) * moveT;
            if (Math.abs(targetY - y) < 0.25F) {
                y = targetY;
            }

            if (exiting) {
                long fadeElapsed = Math.max(0L, nowMs - exitStartedMs);
                alpha = 1.0F - Mth.clamp((float) fadeElapsed / (float) TASK_FADE_MS, 0.0F, 1.0F);
            } else {
                float fadeStep =
                        TASK_FADE_MS <= 0L ? 1.0F : Mth.clamp((float) elapsed / (float) TASK_FADE_MS, 0.0F, 1.0F);
                alpha = Math.min(1.0F, alpha + fadeStep);
            }
        }
    }
}
