package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingModuleCell;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibGuiRenderState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibScrollBar;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibTaskCards;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibTextRender;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
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
    // UI 尺寸与布局常量
    public static final int UI_WIDTH = 304;
    public static final int UI_HEIGHT = 192;
    private static final float TEXT_SCALE = 0.8F;
    private static final long ENERGY_GAUGE_REFERENCE = 1_000_000_000L;
    private static final int HEADER_STATUS_LABEL_COLOR = 0xFF5D5D5D;
    private static final int HEADER_STATUS_SUCCESS_COLOR = 0xFF00A850;
    private static final int HEADER_STATUS_ERROR_COLOR = 0xFFC03434;
    private static final int HEADER_STATUS_MUTED_COLOR = 0xFF606060;
    private static final ThreadLocal<DecimalFormat> PERFORMANCE_MS_FORMAT =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US)));

    // 模块贴图资源路径
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

    // 主面板 / 工具栏布局常量
    private static final int PANEL_MARGIN = 6;
    private static final int MAIN_PANEL_X = PANEL_MARGIN;
    private static final int MAIN_PANEL_Y = 20;
    private static final int MAIN_PANEL_W = UI_WIDTH - PANEL_MARGIN * 2;
    private static final int MAIN_PANEL_H = 83;
    private static final int TOOLBAR_BUTTON_SIZE = 14;
    private static final int TOOLBAR_BUTTON_STRIDE = TOOLBAR_BUTTON_SIZE + 3;
    private static final int TOOLBAR_X = UI_WIDTH - PANEL_MARGIN - TOOLBAR_BUTTON_SIZE * 3 - 3 * 2;
    private static final int TOOLBAR_Y = 4;

    // 模块预览区域布局常量
    private static final int MODULE_AREA_X = MAIN_PANEL_X + 6;
    private static final int MODULE_AREA_Y = MAIN_PANEL_Y + 6;
    private static final int MODULE_AREA_W = MAIN_PANEL_W - 12;
    private static final int MODULE_AREA_H = 62;
    private static final int MODULE_GRID_X = MODULE_AREA_X + 6;
    private static final int MODULE_GRID_Y = MODULE_AREA_Y + 14;
    private static final int MODULE_GRID_W = MODULE_AREA_W - 12;
    private static final int MODULE_GRID_H = MODULE_AREA_H - 18;

    // 状态 / 统计 / 仪表盘区域布局常量
    private static final int MIDDLE_AREA_Y = MAIN_PANEL_Y + 6;
    private static final int STATUS_AREA_X = MODULE_AREA_X;
    private static final int STATUS_AREA_Y = MIDDLE_AREA_Y;
    private static final int STATUS_AREA_W = 76;
    private static final int STATUS_AREA_H = 70;
    private static final int STATUS_ROW_X = STATUS_AREA_X + 8;
    private static final int STATUS_TEXT_GAP = 16;
    private static final int STATUS_VALUE_RIGHT_PAD = 6;
    private static final int STATS_AREA_X = STATUS_AREA_X + STATUS_AREA_W + 6;
    private static final int STATS_AREA_Y = MIDDLE_AREA_Y;
    private static final int STATS_AREA_W = 126;
    private static final int STATS_AREA_H = 70;
    private static final int GAUGE_AREA_X = STATS_AREA_X + STATS_AREA_W + 6;
    private static final int GAUGE_AREA_Y = MIDDLE_AREA_Y;
    private static final int GAUGE_AREA_W = MODULE_AREA_X + MODULE_AREA_W - GAUGE_AREA_X;
    private static final int GAUGE_AREA_H = 70;
    private static final int GAUGE_BAR_Y = GAUGE_AREA_Y + 19;
    private static final int GAUGE_BAR_H = 32;
    private static final int GAUGE_BAR_W = 23;

    // 玩家背包格子布局常量
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INV_X = MODULE_AREA_X;
    private static final int PLAYER_INV_LABEL_Y = MAIN_PANEL_Y + MAIN_PANEL_H - 2;
    private static final int PLAYER_INV_Y = PLAYER_INV_LABEL_Y + 10;
    private static final int PLAYER_HOTBAR_Y = PLAYER_INV_Y + SLOT_SIZE * 3 + 2;

    // 任务面板布局常量
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
    private static final int TASK_LIST_BOTTOM_Y = TASK_PANEL_Y + TASK_PANEL_H - 4;
    private static final int TASK_SCROLLBAR_W = 3;

    // 任务卡片动画时间常量
    private static final long TASK_FADE_MS = 360L;
    private static final long TASK_MOVE_MS = 140L;

    // 实例字段
    private final ECOCraftingSystemBlockEntity crafting;
    private final Inventory playerInventory;
    private final Map<String, TaskCardAnimation> taskAnimations = new LinkedHashMap<>();
    private final NEAe2IconButtonWidget[] toolbarButtons = new NEAe2IconButtonWidget[3];
    private int taskScrollOffset;
    private int lastTaskScrollOffset;

    // 构造方法
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

    // 不显示默认标题栏（自定义标题绘制）
    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    // 初始化 LDLib 子控件（工具栏按钮 + 背包格子）
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

    // 绘制背景层（深色面板 + 进度条 + 仪表盘 + 工具栏按钮背景）
    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int ox = getPositionX();
        int oy = getPositionY();
        NECraftingUiState state = currentState();
        updateToolbarIcons(state);

        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + STATUS_AREA_X, oy + STATUS_AREA_Y, STATUS_AREA_W, STATUS_AREA_H);
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + STATS_AREA_X, oy + STATS_AREA_Y, STATS_AREA_W, STATS_AREA_H);
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + GAUGE_AREA_X, oy + GAUGE_AREA_Y, GAUGE_AREA_W, GAUGE_AREA_H);
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
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + TASK_PANEL_X, oy + TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H);
    }

    // 绘制前景层（标题 + 状态 / 统计 / 模块 / 仪表盘 / 任务面板）
    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NECraftingUiState state = currentState();
        drawLine(graphics, title, 8, 8, TEXT_PRIMARY);
        drawHeaderMachineStatus(graphics, state);
        drawStatusArea(graphics, state);
        drawStatsArea(graphics, state);
        drawGaugeLabels(graphics);
        drawLine(
                graphics,
                Component.translatable("gui.neoecoae.common.inventory"),
                PLAYER_INV_X,
                PLAYER_INV_LABEL_Y,
                TEXT_MUTED);
        drawTaskPanel(graphics, state);
    }

    // 绘制鼠标悬浮提示（工具栏 / 模块 / 仪表盘 / 任务卡片 / 统计）
    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (renderToolbarTooltip(graphics, mouseX, mouseY)) {
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

    // 添加工具栏按钮（超频 / 主动冷却 / 自动清理）
    private void addToolbarButton(
            int index, java.util.function.Consumer<com.lowdragmc.lowdraglib.gui.util.ClickData> action) {
        toolbarButtons[index] = new NEAe2IconButtonWidget(
                TOOLBAR_X + index * TOOLBAR_BUTTON_STRIDE,
                TOOLBAR_Y,
                TOOLBAR_BUTTON_SIZE,
                TOOLBAR_BUTTON_SIZE,
                toolbarIcon(currentState(), index),
                action);
        addWidget(toolbarButtons[index]);
    }

    // 添加玩家背包格子控件
    private void addPlayerInventorySlots() {
        NEPlayerInventoryWidgets.addPlayerInventorySlots(
                this, playerInventory, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
    }

    // 鼠标滚轮滚动任务面板
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

    // 绘制模块预览区标签和网格
    private void drawModuleLabels(GuiGraphics g, NECraftingUiState state) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.module_preview"),
                MODULE_AREA_X + 8,
                MODULE_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawScaledRight(
                g,
                Component.literal("FT " + NELDLibText.number(state.parallelCount()) + "   FX "
                        + NELDLibText.number(state.workerCount())),
                absX(MODULE_AREA_X + MODULE_AREA_W - 8),
                absY(MODULE_AREA_Y + 5),
                NELDLibStyle.DARK_TEXT_VALUE);
        if (crafting.getBuildDefinition() == null) {
            drawScaledCentered(
                    g,
                    Component.translatable("emi.neoecoae.multiblock.empty_scene"),
                    absX(MODULE_AREA_X),
                    absY(MODULE_AREA_Y + 39),
                    MODULE_AREA_W,
                    NELDLibStyle.DARK_TEXT_MUTED);
            return;
        }
        drawModulePlane(g, state);
    }

    // 绘制模块网格（三行：上并行核心 / 工作核心 / 下并行核心）
    private void drawModulePlane(GuiGraphics g, NECraftingUiState state) {
        ModuleGrid grid = moduleGrid(state);
        if (grid.columns() <= 0) {
            drawScaledCentered(
                    g,
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

    // 绘制单个模块格子（背景 + 核心纹理 + 发光纹理）
    private void drawModuleCell(
            GuiGraphics g, int x, int y, int size, NECraftingModuleCell cell, NECraftingModuleCell.Row row) {
        boolean active = cell != null;
        ResourceLocation baseTexture =
                active ? row == NECraftingModuleCell.Row.WORKER ? MODULE_CORE_SIDE : MODULE_PARALLEL_CORE_FRONT : null;
        ResourceLocation overlayTexture =
                active && row != NECraftingModuleCell.Row.WORKER ? lightForTier(cell.tier()) : null;
        drawTexturedModuleSlot(g, absX(x), absY(y), size, baseTexture, overlayTexture, active);
    }

    // 绘制带纹理的模块槽位（面板背景 + 底图 + 发光层 + 灰色遮罩）
    private void drawTexturedModuleSlot(
            GuiGraphics g,
            int x,
            int y,
            int size,
            ResourceLocation baseTexture,
            ResourceLocation overlayTexture,
            boolean active) {
        if (size >= 10) {
            NELDLibClientStyle.drawDarkInsetRect(g, x, y, size, size);
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

    // 渲染模块悬浮提示（工作核心 / 并行核心详情）
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

    // 工作核心悬浮提示（当前产出物品 + 坐标）
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

    // 并行核心悬浮提示（等级 / 每核心并行数 / 坐标）
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

    // 绘制状态区域（超频 / 主动冷却 / 自动清理开关）
    private void drawStatusArea(GuiGraphics g, NECraftingUiState state) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.status"),
                STATUS_AREA_X + 8,
                STATUS_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        int y = STATUS_AREA_Y + 21;
        drawStatusRow(
                g, Component.translatable("gui.neoecoae.crafting.overclock"), state.overclocked(), STATUS_ROW_X, y);
        y += 15;
        drawStatusRow(
                g,
                Component.translatable("gui.neoecoae.crafting.cooling_short"),
                state.activeCooling(),
                STATUS_ROW_X,
                y);
        y += 15;
        drawStatusRow(
                g,
                Component.translatable("gui.neoecoae.crafting.waste_short"),
                state.autoClearCoolingWaste(),
                STATUS_ROW_X,
                y);
    }

    // 绘制统计区域（配方槽 / 批量并行 / 样板数 / 核心数）
    private void drawStatsArea(GuiGraphics g, NECraftingUiState state) {
        int x = STATS_AREA_X + 8;
        int rightX = STATS_AREA_X + STATS_AREA_W - 8;
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.stats"),
                x,
                STATS_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawScaledRight(
                g,
                Component.literal(formatPerformanceCornerValue(state.performanceAverageNanos())),
                absX(rightX),
                absY(STATS_AREA_Y + 5),
                NELDLibStyle.DARK_TEXT_VALUE);
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
                x + 64,
                y);
    }

    // 绘制仪表盘（能量 / 冷却液柱状图）
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

    // 绘制仪表盘标签（能量 / 冷却液文字）
    private void drawGaugeLabels(GuiGraphics g) {
        drawScaledFittedString(
                g,
                Component.translatable("gui.neoecoae.crafting.energy_cooling"),
                absX(GAUGE_AREA_X + 8),
                absY(GAUGE_AREA_Y + 5),
                GAUGE_AREA_W - 16,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        int gaugeY = GAUGE_BAR_Y;
        int gaugeH = GAUGE_BAR_H;
        int labelY = absY(gaugeY + gaugeH + 1);
        int columnW = (GAUGE_AREA_W - 16) / 2;
        drawScaledCenteredFitted(
                g,
                Component.translatable("gui.neoecoae.crafting.energy_short"),
                absX(GAUGE_AREA_X + 6),
                labelY,
                columnW,
                NELDLibStyle.DARK_TEXT_MUTED);
        drawScaledCenteredFitted(
                g,
                Component.translatable("gui.neoecoae.crafting.cooling_short"),
                absX(GAUGE_AREA_X + GAUGE_AREA_W - 6 - columnW),
                labelY,
                columnW,
                NELDLibStyle.DARK_TEXT_MUTED);
    }

    private void updateToolbarIcons(NECraftingUiState state) {
        for (int index = 0; index < toolbarButtons.length; index++) {
            if (toolbarButtons[index] != null) {
                toolbarButtons[index].setIcon(toolbarIcon(state, index));
            }
        }
    }

    private static Icon toolbarIcon(NECraftingUiState state, int index) {
        return switch (index) {
            case 0 -> state.overclocked() ? Icon.LEVEL_ENERGY : Icon.POWER_UNIT_AE;
            case 1 -> state.activeCooling() ? Icon.FLUID_SUBSTITUTION_ENABLED : Icon.FLUID_SUBSTITUTION_DISABLED;
            default -> state.autoClearCoolingWaste() ? Icon.CONDENSER_OUTPUT_TRASH : Icon.BACKGROUND_TRASH;
        };
    }

    // 绘制状态行（指示灯 + 开关状态文字）
    private void drawStatusRow(GuiGraphics g, Component label, boolean enabled, int x, int y) {
        int absX = absX(x);
        int absY = absY(y);
        NELDLibClientStyle.drawDarkInsetRect(g, absX, absY - 3, 13, 13);
        int light = enabled ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR;
        g.fill(absX + 4, absY + 1, absX + 9, absY + 6, light);
        Component value = Component.translatable(enabled ? "gui.neoecoae.common.on" : "gui.neoecoae.common.off");
        int valueRight = absX(STATUS_AREA_X + STATUS_AREA_W - STATUS_VALUE_RIGHT_PAD);
        int labelX = absX + STATUS_TEXT_GAP;
        int labelMaxW = Math.max(8, valueRight - scaledWidth(value) - 4 - labelX);
        drawScaledFittedString(g, label, labelX, absY, labelMaxW, NELDLibStyle.DARK_TEXT_MUTED);
        drawScaledRight(
                g, value, valueRight, absY, enabled ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR);
    }

    // 绘制标题栏状态文字（formed / active）
    private void drawHeaderMachineStatus(GuiGraphics g, NECraftingUiState state) {
        Component formedLabel =
                Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component formedValue = boolText(state.formed());
        Component activeLabel = Component.literal("  ")
                .append(Component.translatable("gui.neoecoae.machine.active"))
                .append(": ");
        Component activeValue = boolText(state.active());
        int textW = scaledWidth(formedLabel)
                + scaledWidth(formedValue)
                + scaledWidth(activeLabel)
                + scaledWidth(activeValue);
        int titleRight = 8 + scaledWidth(title) + 10;
        int rightLimit = TOOLBAR_X - 8;
        int textX = absX(Math.min(titleRight, Math.max(8, rightLimit - textW)));
        int textY = absY(8);
        textX += drawScaledString(g, formedLabel, textX, textY, HEADER_STATUS_LABEL_COLOR);
        textX += drawScaledString(
                g, formedValue, textX, textY, state.formed() ? HEADER_STATUS_SUCCESS_COLOR : HEADER_STATUS_ERROR_COLOR);
        textX += drawScaledString(g, activeLabel, textX, textY, HEADER_STATUS_LABEL_COLOR);
        drawScaledString(
                g, activeValue, textX, textY, state.active() ? HEADER_STATUS_SUCCESS_COLOR : HEADER_STATUS_MUTED_COLOR);
    }

    // 绘制玩家背包格子背景
    private void drawPlayerInventorySlots(GuiGraphics graphics) {
        NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                graphics, this::absX, this::absY, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
    }

    // 绘制任务面板（标题 + 任务卡片列表 + 滚动条）
    private void drawTaskPanel(GuiGraphics g, NECraftingUiState state) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.tasks"),
                TASK_PANEL_X + 8,
                TASK_PANEL_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawScaledRight(
                g,
                Component.literal(NELDLibText.number(state.recipeEntries().size())),
                absX(TASK_PANEL_X + TASK_PANEL_W - 8),
                absY(TASK_PANEL_Y + 5),
                NELDLibStyle.DARK_TEXT_VALUE);

        taskScrollOffset =
                clampTaskScrollOffset(taskScrollOffset, state.recipeEntries().size());
        List<TaskCardAnimation> cards = updateTaskAnimations(state);
        if (cards.isEmpty() && state.recipeEntries().isEmpty()) {
            drawScaledCentered(
                    g,
                    Component.translatable("gui.neoecoae.crafting.no_tasks"),
                    absX(TASK_PANEL_X + 6),
                    absY(TASK_PANEL_Y + 42),
                    TASK_PANEL_W - 12,
                    NELDLibStyle.DARK_TEXT_MUTED);
            return;
        }

        g.enableScissor(
                absX(TASK_CARD_X), absY(TASK_CARD_Y), absX(TASK_CARD_X + TASK_CARD_W), absY(TASK_LIST_BOTTOM_Y + 1));
        try {
            for (TaskCardAnimation card : cards) {
                drawTaskCard(g, card);
            }
        } finally {
            g.disableScissor();
        }
        drawTaskScrollbar(g, state.recipeEntries().size(), visibleTaskCardCount());
    }

    // 更新任务卡片动画状态（新建 / 移入 / 移出 / 淡入淡出）
    private List<TaskCardAnimation> updateTaskAnimations(NECraftingUiState state) {
        long now = Util.getMillis();
        Set<String> activeKeys = new HashSet<>();
        int total = state.recipeEntries().size();
        int visible = visibleTaskCardCount();
        int scrollDelta = taskScrollOffset - lastTaskScrollOffset;
        lastTaskScrollOffset = taskScrollOffset;
        int firstBufferedIndex = Math.max(0, taskScrollOffset - 1);
        int lastBufferedIndex = Math.min(total, taskScrollOffset + visible + 1);

        for (int entryIndex = firstBufferedIndex; entryIndex < lastBufferedIndex; entryIndex++) {
            NECraftingRecipeUiEntry entry = state.recipeEntries().get(entryIndex);
            String key = taskEntryKey(entry, entryIndex);
            activeKeys.add(key);
            int targetY = TASK_CARD_Y + (entryIndex - taskScrollOffset) * TASK_CARD_STRIDE;
            TaskCardAnimation animation = taskAnimations.get(key);
            if (animation == null) {
                float entryOffset = scrollDelta > 0 ? TASK_CARD_STRIDE : scrollDelta < 0 ? -TASK_CARD_STRIDE : 5.0F;
                animation = new TaskCardAnimation(entry, targetY, entryOffset);
                taskAnimations.put(key, animation);
            }
            animation.entry = entry;
            animation.targetY = targetY;
            animation.exiting = false;
        }

        Iterator<Map.Entry<String, TaskCardAnimation>> iterator =
                taskAnimations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TaskCardAnimation> entry = iterator.next();
            TaskCardAnimation animation = entry.getValue();
            if (!activeKeys.contains(entry.getKey()) && !animation.exiting) {
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

    // 绘制单张任务卡片（物品图标 + 名称 + 数量 + 进度条）
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

        NELDLibTaskCards.drawCardRect(
                g, absX, absY, TASK_CARD_W, TASK_CARD_H, alpha, NELDLibTaskCards.statusColor(entry.status()));
        if (alpha > 0.22F && !entry.output().isEmpty()) {
            NELDLibGuiRenderState.beginVanillaGuiItemBatch(g);
            try {
                NELDLibGuiRenderState.renderVanillaSlotItem(g, font(), entry.output(), absX + 1, absY, "");
            } finally {
                NELDLibGuiRenderState.endVanillaGuiItemBatch(g);
            }
        }

        int textX = x + 20;
        int textY = y + 4;
        String amountText = "x" + NELDLibText.compactTaskAmount(entry.outputAmount());
        int amountW = scaledWidth(amountText);
        int maxNameW = Math.max(16, TASK_CARD_W - 28 - amountW);
        String name = fitText(entry.output().getHoverName().getString(), maxNameW);
        drawScaledString(
                g, name, absX(textX), absY(textY), NELDLibTaskCards.withAlpha(NELDLibStyle.DARK_TEXT_PRIMARY, alpha));
        drawScaledRight(
                g,
                Component.literal(amountText),
                absX(TASK_CARD_X + TASK_CARD_W - 5),
                absY(textY),
                NELDLibTaskCards.withAlpha(NELDLibStyle.DARK_TEXT_VALUE, alpha));
        NELDLibTaskCards.drawProgressBar(g, absX + 20, absY + TASK_CARD_H - 4, TASK_CARD_W - 25, 2, entry, alpha);
    }

    // 绘制任务面板滚动条
    private void drawTaskScrollbar(GuiGraphics g, int total, int visible) {
        if (total <= visible) {
            return;
        }
        NELDLibScrollBar.drawVertical(
                g,
                absX(TASK_PANEL_X + TASK_PANEL_W - 5),
                absY(TASK_CARD_Y),
                TASK_SCROLLBAR_W,
                Math.max(1, TASK_LIST_BOTTOM_Y - TASK_CARD_Y - 1),
                total,
                visible,
                taskScrollOffset,
                0xAA17141E,
                0xFF8B83A0,
                10);
    }

    // 渲染任务卡片悬浮提示（物品信息 + 状态 + 数量 + 进度）
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
            lines.add(Component.translatable(NELDLibTaskCards.statusKey(entry.status())));
            lines.add(Component.translatable(
                    "gui.neoecoae.crafting.task.amount", NELDLibText.compactTaskAmount(entry.outputAmount())));
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

    // 计算任务面板可见卡片数量
    private int visibleTaskCardCount() {
        int space = TASK_LIST_BOTTOM_Y - TASK_CARD_Y;
        if (space < TASK_CARD_H) {
            return 1;
        }
        return Math.max(1, 1 + (space - TASK_CARD_H) / TASK_CARD_STRIDE);
    }

    // 限制滚动偏移量不超出范围
    private int clampTaskScrollOffset(int value, int total) {
        return Mth.clamp(value, 0, Math.max(0, total - visibleTaskCardCount()));
    }

    // 绘制垂直柱状仪表盘（能量 / 冷却液）
    private void drawVerticalReserveGauge(
            GuiGraphics g, int x, int y, int w, int h, int accentColor, double fillRatio) {
        NELDLibClientStyle.drawDarkInsetRect(g, x, y, w, h);
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

    // 绘制线程使用率水平进度条
    private void drawThreadUsageBar(GuiGraphics g, int x, int y, int w, int h, long current, long max) {
        NELDLibClientStyle.drawDarkInsetRect(g, x, y, w, h);
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

    // 渲染工具栏按钮悬浮提示（超频 / 冷却 / 自动清理）
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

    // 渲染仪表盘悬浮提示（能耗 / 冷却液数值）
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

    // 渲染统计区域悬浮提示（核心等级分布 / 并行数详情）
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
        lines.add(Component.literal(formatPerformanceLine(state.performanceAverageNanos())));
        g.renderTooltip(font(), lines, Optional.empty(), mouseX, mouseY);
    }

    // 绘制一行缩放文字
    private void drawLine(GuiGraphics g, Component text, int x, int y, int color) {
        drawScaledString(g, text, absX(x), absY(y), color);
    }

    // 绘制标签 + 数值行（label: value）
    private void drawInlineValueLine(GuiGraphics g, String label, long value, int x, int y) {
        int cursor = drawScaledString(g, label, absX(x), absY(y), NELDLibStyle.DARK_TEXT_MUTED);
        drawScaledString(g, NELDLibText.number(value), absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_VALUE);
    }

    // 绘制紧凑成对行（label: current / max）
    private void drawCompactPairLine(GuiGraphics g, String label, long current, long max, int x, int y) {
        int cursor = drawScaledString(g, label, absX(x), absY(y), NELDLibStyle.DARK_TEXT_MUTED);
        cursor += drawScaledString(
                g, NELDLibText.number(current), absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_SUCCESS);
        cursor += drawScaledString(g, " / ", absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_MUTED);
        drawScaledString(g, NELDLibText.number(max), absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_VALUE);
    }

    // 绘制缩放文字（0.8x 缩放），返回文字宽度
    private int drawScaledString(GuiGraphics g, Component text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        g.drawString(font(), text, 0, 0, color, false);
        g.pose().popPose();
        return scaledWidth(text);
    }

    // 绘制缩放文字（String 重载）
    private int drawScaledString(GuiGraphics g, String text, int x, int y, int color) {
        return drawScaledString(g, Component.literal(text), x, y, color);
    }

    // 右对齐绘制缩放文字
    private void drawScaledRight(GuiGraphics g, Component text, int rightX, int y, int color) {
        drawScaledString(g, text, rightX - scaledWidth(text), y, color);
    }

    // 居中绘制缩放文字
    private void drawScaledCentered(GuiGraphics g, Component text, int x, int y, int width, int color) {
        drawScaledString(g, text, x + (width - scaledWidth(text)) / 2, y, color);
    }

    private void drawScaledCenteredFitted(GuiGraphics g, Component text, int x, int y, int width, int color) {
        String fitted = fitText(text.getString(), width);
        drawScaledString(g, fitted, x + (width - scaledWidth(fitted)) / 2, y, color);
    }

    private void drawScaledFittedString(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        drawScaledString(g, fitText(text.getString(), maxWidth), x, y, color);
    }

    // 计算缩放后文字宽度
    private int scaledWidth(Component text) {
        return Math.round(font().width(text) * TEXT_SCALE);
    }

    private int scaledWidth(String text) {
        return Math.round(font().width(text) * TEXT_SCALE);
    }

    private static String formatPerformanceLine(long averageNanos) {
        return Component.translatable("gui.neoecoae.crafting.performance").getString() + ":"
                + formatPerformanceValue(averageNanos);
    }

    private static String formatPerformanceCornerValue(long averageNanos) {
        long safeNanos = Math.max(0L, averageNanos);
        long micros = Math.round(safeNanos / 1_000.0D);
        if (micros < 1_000L) {
            return micros + " \u03bcs";
        }
        return PERFORMANCE_MS_FORMAT.get().format(safeNanos / 1_000_000.0D) + " ms";
    }

    private static String formatPerformanceValue(long averageNanos) {
        long safeNanos = Math.max(0L, averageNanos);
        long micros = Math.round(safeNanos / 1_000.0D);
        String millis = PERFORMANCE_MS_FORMAT.get().format(safeNanos / 1_000_000.0D);
        return micros + " \u03bcs/" + millis + " ms";
    }

    // 统计指定等级的并行核心数量
    private static int countTier(NECraftingUiState state, int tier) {
        int count = 0;
        for (int value : state.parallelCoreTiers()) {
            if (value == tier) {
                count++;
            }
        }
        return count;
    }

    // 获取每核心并行数（按等级和超频状态）
    private static int parallelPerCore(int tier, boolean overclocked) {
        return switch (tier) {
            case 3 -> overclocked ? 384 : 256;
            case 2 -> overclocked ? 96 : 72;
            default -> overclocked ? 32 : 24;
        };
    }

    // 生成任务卡片唯一键（用于动画追踪）
    private static String taskEntryKey(NECraftingRecipeUiEntry entry, int index) {
        return entry.id() == null || entry.id().isBlank() ? "task:" + index : entry.id();
    }

    // 文本截断（缩放适配 + 省略号）
    private String fitText(String text, int maxWidth) {
        return NELDLibTextRender.fitScaledWithEllipsis(font(), text, maxWidth, TEXT_SCALE);
    }

    // 格式化 tick 为可读时间（t / s / m s）
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

    // 计算比例宽度（current / max * fullWidth）
    private static int ratioWidth(long current, long max, int fullWidth) {
        return NELDLibTaskCards.ratioWidth(current, max, fullWidth);
    }

    // 安全计算比例（0~1 之间）
    private static double clampRatio(long value, long max) {
        if (value <= 0 || max <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (double) value / (double) max));
    }

    // 能耗仪表盘颜色（按比例渐变：绿 → 黄 → 红）
    private static int energyGaugeColor(double ratio) {
        if (ratio >= 0.9D) {
            return NELDLibStyle.DARK_TEXT_ERROR;
        }
        if (ratio >= 0.5D) {
            return NELDLibStyle.DARK_TEXT_WARNING;
        }
        return NELDLibStyle.DARK_TEXT_SUCCESS;
    }

    // 并行核心等级 → 发光纹理映射
    private static ResourceLocation lightForTier(int tier) {
        return switch (tier) {
            case 3 -> MODULE_PARALLEL_CORE_LIGHT_L9;
            case 2 -> MODULE_PARALLEL_CORE_LIGHT_L6;
            default -> MODULE_PARALLEL_CORE_LIGHT_L4;
        };
    }

    // 并行核心等级 → 名称语言键映射
    private static String parallelCoreNameKey(int tier) {
        return switch (tier) {
            case 3 -> "block.neoecoae.crafting_parallel_core_l9";
            case 2 -> "block.neoecoae.crafting_parallel_core_l6";
            default -> "block.neoecoae.crafting_parallel_core_l4";
        };
    }

    // 根据列和行查找模块格子
    private static NECraftingModuleCell moduleCellAt(
            NECraftingUiState state, int column, NECraftingModuleCell.Row row) {
        for (NECraftingModuleCell cell : state.moduleCells()) {
            if (cell.column() == column && cell.row() == row) {
                return cell;
            }
        }
        return null;
    }

    // 格式化模块方块坐标
    private static String formatModulePos(NECraftingModuleCell cell) {
        if (cell == null || cell.pos() == null) {
            return "";
        }
        BlockPos pos = cell.pos();
        return "x=" + pos.getX() + ", y=" + pos.getY() + ", z=" + pos.getZ();
    }

    // 计算模块网格布局（自适应格子大小和居中）
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

    // 模块网格布局记录（含行 Y 坐标计算）
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

    // 任务卡片动画状态（移动 + 淡入淡出）
    private static final class TaskCardAnimation {
        private NECraftingRecipeUiEntry entry;
        private float y;
        private int targetY;
        private float alpha;
        private long lastUpdateMs;
        private boolean exiting;
        private long exitStartedMs;

        // 构造方法（初始位置偏移 + 透明度 0）
        private TaskCardAnimation(NECraftingRecipeUiEntry entry, int targetY, float entryOffset) {
            this.entry = entry;
            this.targetY = targetY;
            this.lastUpdateMs = Util.getMillis();
            this.y = targetY + entryOffset;
            this.alpha = 0.0F;
        }

        // 更新动画帧（位置插值 + 透明度变化）
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
