package cn.dancingsnow.neoecoae.gui.host;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class NECraftingAeCanvas extends NEHostCanvas {
    static final int UI_WIDTH = 304;
    static final int UI_HEIGHT = 216;
    private static final int EDGE = 7;
    private static final int GAP = 7;
    private static final int HEADER_Y = EDGE;
    private static final float TEXT_SCALE = 0.95F;
    private static final float HEADER_TEXT_SCALE = 0.95F;
    private static final float MODULE_TEXT_SCALE = 0.82F;
    private static final float TASK_ITEM_SCALE = 0.95F;
    private static final long ENERGY_GAUGE_REFERENCE = 1_000_000_000L;
    private static final ResourceLocation CORE_SIDE = NeoECOAE.id("textures/block/crafting/core/core_side.png");
    private static final ResourceLocation PARALLEL_FRONT = NeoECOAE.id("textures/block/crafting/core/parallel_core_north.png");
    private static final ResourceLocation LIGHT_L4 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_a.png");
    private static final ResourceLocation LIGHT_L6 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_b.png");
    private static final ResourceLocation LIGHT_L9 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_c.png");

    static final int TOOLBAR_BUTTON_SIZE = 16;
    private static final int TOOLBAR_BUTTON_GAP = 3;
    static final int TOOLBAR_BUTTON_STRIDE = TOOLBAR_BUTTON_SIZE + TOOLBAR_BUTTON_GAP;
    static final int TOOLBAR_X = UI_WIDTH - EDGE - TOOLBAR_BUTTON_SIZE * 3 - TOOLBAR_BUTTON_GAP * 2;
    static final int TOOLBAR_Y = HEADER_Y;

    private static final int TOP_AREA_X = EDGE;
    private static final int TOP_AREA_Y = TOOLBAR_Y + TOOLBAR_BUTTON_SIZE + GAP;
    private static final int TOP_AREA_W = UI_WIDTH - EDGE * 2;
    private static final int TOP_AREA_H = 88;
    private static final int STATUS_AREA_X = TOP_AREA_X;
    private static final int STATUS_AREA_Y = TOP_AREA_Y;
    private static final int STATUS_AREA_W = 66;
    private static final int STATUS_AREA_H = TOP_AREA_H;
    private static final int STATUS_LIGHT_SIZE = 15;
    private static final int STATUS_LIGHT_GAP = 6;
    private static final int STATUS_LIGHT_GROUP_H = STATUS_LIGHT_SIZE * 3 + STATUS_LIGHT_GAP * 2;
    private static final int STATUS_LIGHT_X = STATUS_AREA_X + EDGE;
    private static final int STATUS_LIGHT_START_Y = STATUS_AREA_Y + 19;
    private static final int STATUS_ROW_0_Y = STATUS_LIGHT_START_Y + 4;
    private static final int STATUS_ROW_1_Y = STATUS_ROW_0_Y + STATUS_LIGHT_SIZE + STATUS_LIGHT_GAP;
    private static final int STATUS_ROW_2_Y = STATUS_ROW_1_Y + STATUS_LIGHT_SIZE + STATUS_LIGHT_GAP;
    private static final int STATS_AREA_X = STATUS_AREA_X + STATUS_AREA_W + GAP;
    private static final int STATS_AREA_Y = TOP_AREA_Y;
    private static final int STATS_AREA_W = 132;
    private static final int STATS_AREA_H = TOP_AREA_H;
    private static final int MODULE_AREA_X = STATS_AREA_X;
    private static final int MODULE_AREA_Y = STATS_AREA_Y;
    private static final int MODULE_AREA_W = STATS_AREA_W;
    private static final int MODULE_AREA_H = STATS_AREA_H;
    private static final int MODULE_GRID_X = MODULE_AREA_X + EDGE;
    private static final int MODULE_GRID_Y = MODULE_AREA_Y + 24;
    private static final int MODULE_GRID_W = MODULE_AREA_W - EDGE * 2;
    private static final int MODULE_GRID_H = 37;
    private static final int MODULE_SCROLLBAR_Y = MODULE_GRID_Y + MODULE_GRID_H + 2;
    private static final int MODULE_SCROLLBAR_H = 3;
    private static final int MODULE_STATS_Y = MODULE_SCROLLBAR_Y + MODULE_SCROLLBAR_H + 3;
    private static final int MODULE_PROGRESS_Y = MODULE_AREA_Y + MODULE_AREA_H - 8;
    private static final int MODULE_PROGRESS_H = 4;
    private static final int GAUGE_AREA_X = STATS_AREA_X + STATS_AREA_W + GAP;
    private static final int GAUGE_AREA_Y = TOP_AREA_Y;
    private static final int GAUGE_AREA_W = TOP_AREA_X + TOP_AREA_W - GAUGE_AREA_X;
    private static final int GAUGE_AREA_H = TOP_AREA_H;
    private static final int GAUGE_BAR_Y = GAUGE_AREA_Y + 26;
    private static final int GAUGE_BAR_H = 45;
    private static final int GAUGE_BAR_W = 24;
    private static final int GAUGE_BAR_GAP = 7;
    private static final int GAUGE_PAIR_W = GAUGE_BAR_W * 2 + GAUGE_BAR_GAP;
    private static final int GAUGE_ENERGY_X = GAUGE_AREA_X + (GAUGE_AREA_W - GAUGE_PAIR_W) / 2;
    private static final int GAUGE_COOLANT_X = GAUGE_ENERGY_X + GAUGE_BAR_W + GAUGE_BAR_GAP;
    static final int PLAYER_INV_X = TOP_AREA_X;
    private static final int PLAYER_INV_LABEL_Y = TOP_AREA_Y + TOP_AREA_H + GAP;
    static final int PLAYER_HOTBAR_Y = UI_HEIGHT - EDGE - SLOT_SIZE;
    static final int PLAYER_INV_Y = PLAYER_HOTBAR_Y - SLOT_SIZE * 3 - 2;
    private static final int TASK_PANEL_X = PLAYER_INV_X + PLAYER_INVENTORY_WIDTH + GAP;
    private static final int TASK_PANEL_Y = PLAYER_INV_LABEL_Y;
    private static final int TASK_PANEL_W = UI_WIDTH - EDGE - TASK_PANEL_X;
    private static final int TASK_PANEL_H = UI_HEIGHT - EDGE - TASK_PANEL_Y;
    private static final int TASK_CARD_X = TASK_PANEL_X + EDGE;
    private static final int TASK_CARD_Y = TASK_PANEL_Y + 19;
    private static final int TASK_CARD_W = TASK_PANEL_W - EDGE * 2;
    private static final int TASK_CARD_H = 17;
    private static final int TASK_CARD_STRIDE = 19;
    private static final int TASK_LIST_BOTTOM_Y = TASK_PANEL_Y + TASK_PANEL_H - EDGE;
    private static final NEAnimatedTaskCards.Layout TASK_LAYOUT = new NEAnimatedTaskCards.Layout(
            TASK_PANEL_X, TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H,
            TASK_CARD_X, TASK_CARD_Y, TASK_CARD_W, TASK_CARD_H, TASK_CARD_STRIDE, TASK_LIST_BOTTOM_Y,
            TASK_CARD_X, TASK_CARD_W,
            TASK_PANEL_X + TASK_PANEL_W - 4, 2, TASK_LIST_BOTTOM_Y - TASK_CARD_Y - 1
    );
    private static final NEAnimatedTaskCards.CardStyle TASK_CARD_STYLE =
            NEAnimatedTaskCards.CardStyle.crafting(TEXT_SCALE, TASK_ITEM_SCALE);

    private final ECOCraftingSystemBlockEntity crafting;
    private final NEAnimatedTaskCards taskCards = new NEAnimatedTaskCards();
    private final ScrollModel moduleScroll = new ScrollModel();
    private CraftingSnapshot snapshot = CraftingSnapshot.EMPTY;

    NECraftingAeCanvas(ECOCraftingSystemBlockEntity crafting) {
        super(UI_WIDTH, UI_HEIGHT);
        this.crafting = crafting;
        bindSnapshot();
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            HoverTooltips tooltip = tooltipAt(currentMouseX(), currentMouseY());
            if (tooltip != null) {
                event.hoverTooltips = tooltip;
            }
        });
    }

    @Override
    protected byte[] encodeSnapshot() {
        return NEHostSnapshots.encode(buf -> {
            buf.writeBoolean(crafting.isFormed());
            buf.writeBoolean(crafting.isHostActive());
            buf.writeVarInt(Math.max(0, crafting.getWorkerCount()));
            buf.writeVarInt(Math.max(0, crafting.getParallelCount()));
            buf.writeVarInt(Math.max(0, crafting.getPatternBusCount()));
            buf.writeVarInt(Math.max(0, crafting.getRunningThreadCount()));
            buf.writeVarInt(Math.max(0, crafting.getThreadCount()));
            buf.writeVarInt(Math.max(0, crafting.getAvailableThreads()));
            buf.writeBoolean(crafting.isOverclocked());
            buf.writeBoolean(crafting.isActiveCooling());
            buf.writeVarInt(Math.max(0, crafting.getCoolant()));
            buf.writeVarLong(Math.max(0L, crafting.getMaxEnergyUsage()));
            NEHostSnapshots.writeTasks(buf, crafting.createCraftingTasks());
            NEHostSnapshots.writeModuleCells(buf, crafting.createCraftingModuleCells());
            NEHostSnapshots.writeItemStacks(buf, crafting.createCraftingWorkerOutputs());
        });
    }

    @Override
    protected void acceptSnapshot(byte[] snapshotData) {
        NEHostSnapshots.decode(snapshotData, buf -> this.snapshot = new CraftingSnapshot(
            buf.readBoolean(),
            buf.readBoolean(),
            Math.max(0, buf.readVarInt()),
            Math.max(0, buf.readVarInt()),
            Math.max(0, buf.readVarInt()),
            Math.max(0, buf.readVarInt()),
            Math.max(0, buf.readVarInt()),
            Math.max(0, buf.readVarInt()),
            buf.readBoolean(),
            buf.readBoolean(),
            Math.max(0, buf.readVarInt()),
            Math.max(0L, buf.readVarLong()),
            NEHostSnapshots.readTasks(buf),
            NEHostSnapshots.readModuleCells(buf),
            NEHostSnapshots.readItemStacks(buf)
        ));
    }

    @Override
    protected void drawHostBackground(GUIContext context) {
        drawInsetRect(context, STATUS_AREA_X, STATUS_AREA_Y, STATUS_AREA_W, STATUS_AREA_H);
        drawInsetRect(context, STATS_AREA_X, STATS_AREA_Y, STATS_AREA_W, STATS_AREA_H);
        drawInsetRect(context, GAUGE_AREA_X, GAUGE_AREA_Y, GAUGE_AREA_W, GAUGE_AREA_H);
        drawInsetRect(context, TASK_PANEL_X, TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H);
        drawHeader(context);
        drawStatusArea(context);
        drawModulePreview(context);
        drawGaugeArea(context);
        drawInventory(context);
        drawTasks(context);
    }

    private void drawHeader(GUIContext context) {
        Component formedLabel = tr("gui.neoecoae.machine.formed", "Formed").append(": ");
        Component formedValue = boolText(snapshot.formed());
        Component activeLabel = Component.literal("  ").append(tr("gui.neoecoae.machine.active", "Active")).append(": ");
        Component activeValue = boolText(snapshot.active());
        float right = TOOLBAR_X - GAP;
        int width = Math.round((context.mc.font.width(formedLabel) + context.mc.font.width(formedValue)
                + context.mc.font.width(activeLabel) + context.mc.font.width(activeValue)) * HEADER_TEXT_SCALE);
        float x = Math.max(EDGE, right - width);
        drawFittedText(context, crafting.getHostTitle(), EDGE, HEADER_Y + 1, Math.max(40, Math.round(x) - EDGE - GAP), TEXT_TITLE);
        drawScaledText(context, formedLabel, x, HEADER_Y + 1, HEADER_TEXT_SCALE, 0xFF5D5D5D);
        x += context.mc.font.width(formedLabel) * HEADER_TEXT_SCALE;
        drawScaledText(context, formedValue, x, HEADER_Y + 1, HEADER_TEXT_SCALE, snapshot.formed() ? 0xFF00A850 : 0xFFC03434);
        x += context.mc.font.width(formedValue) * HEADER_TEXT_SCALE;
        drawScaledText(context, activeLabel, x, HEADER_Y + 1, HEADER_TEXT_SCALE, 0xFF5D5D5D);
        x += context.mc.font.width(activeLabel) * HEADER_TEXT_SCALE;
        drawScaledText(context, activeValue, x, HEADER_Y + 1, HEADER_TEXT_SCALE, snapshot.active() ? 0xFF00A850 : 0xFF606060);
    }

    private void drawStatusArea(GUIContext context) {
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.status", "Status"), STATUS_AREA_X + EDGE, STATUS_AREA_Y + EDGE,
                TEXT_SCALE, STATUS_AREA_W - EDGE * 2, TEXT_PRIMARY);
        drawStatusRow(context, tr("gui.neoecoae.crafting.overclock", "OC"), snapshot.overclocked(), STATUS_ROW_0_Y);
        drawStatusRow(context, tr("gui.neoecoae.crafting.cooling_short", "Cool"), snapshot.activeCooling(), STATUS_ROW_1_Y);
        drawStatusRow(context, tr("gui.neoecoae.crafting.waste_short", "Waste"), snapshot.coolant() > 0, STATUS_ROW_2_Y);
    }

    private void drawStatusRow(GUIContext context, Component label, boolean enabled, int y) {
        drawInsetRect(context, STATUS_LIGHT_X, y - 4, STATUS_LIGHT_SIZE, STATUS_LIGHT_SIZE);
        fillLocal(context, STATUS_LIGHT_X + 4, y, 7, 7, enabled ? TEXT_SUCCESS : TEXT_ERROR);
        String value = trString(enabled ? "gui.neoecoae.common.on" : "gui.neoecoae.common.off", enabled ? "On" : "Off");
        int valueWidth = Math.round(context.mc.font.width(value) * TEXT_SCALE);
        int labelX = STATUS_LIGHT_X + STATUS_LIGHT_SIZE + GAP;
        int labelMax = STATUS_AREA_X + STATUS_AREA_W - EDGE - labelX - valueWidth;
        drawScaledFittedText(context, label, labelX, y, TEXT_SCALE, labelMax, TEXT_MUTED);
        drawScaledText(context, value, STATUS_AREA_X + STATUS_AREA_W - EDGE - valueWidth, y, TEXT_SCALE,
                enabled ? TEXT_SUCCESS : TEXT_ERROR);
    }

    private void drawModulePreview(GUIContext context) {
        String moduleCounts = "FT " + NEHostFormat.number(snapshot.parallelCount()) + "   FX " + NEHostFormat.number(snapshot.workerCount());
        int countWidth = Math.round(context.mc.font.width(moduleCounts) * MODULE_TEXT_SCALE);
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.module_preview", "Structure Preview"),
                MODULE_AREA_X + EDGE, MODULE_AREA_Y + EDGE, MODULE_TEXT_SCALE,
                MODULE_AREA_W - EDGE * 2 - countWidth - 5, TEXT_PRIMARY);
        drawScaledRightText(context, moduleCounts, MODULE_AREA_X + MODULE_AREA_W - EDGE, MODULE_AREA_Y + EDGE,
                MODULE_TEXT_SCALE, TEXT_VALUE);
        Grid grid = moduleGrid();
        if (grid.columns <= 0) {
            drawScaledCenteredText(context, tr("gui.neoecoae.crafting.no_worker_cores", "No worker cores"),
                    MODULE_AREA_X, MODULE_AREA_Y + 47, MODULE_AREA_W, MODULE_TEXT_SCALE, TEXT_MUTED);
            drawModuleStats(context);
            return;
        }
        if (grid.contentW > MODULE_GRID_W) {
            float thumbW = Math.max(12.0F, MODULE_GRID_W * MODULE_GRID_W / grid.contentW);
            float thumbX = MODULE_GRID_X + (MODULE_GRID_W - thumbW) * moduleScroll.offset() / Math.max(1.0F, moduleScroll.max());
            fillLocal(context, MODULE_GRID_X, MODULE_SCROLLBAR_Y, MODULE_GRID_W, MODULE_SCROLLBAR_H, PANEL_OUTER);
            fillLocal(context, thumbX, MODULE_SCROLLBAR_Y, thumbW, MODULE_SCROLLBAR_H, PANEL_MIDDLE);
            fillLocal(context, thumbX, MODULE_SCROLLBAR_Y, thumbW, 1, PANEL_EDGE);
        }
        context.graphics.flush();
        context.enableScissor(absX(MODULE_GRID_X), absY(MODULE_GRID_Y), MODULE_GRID_W, MODULE_GRID_H);
        for (int column = 0; column < grid.columns; column++) {
            float x = grid.x + column * grid.size;
            if (x + grid.size <= MODULE_GRID_X || x >= MODULE_GRID_X + MODULE_GRID_W) {
                continue;
            }
            drawModuleCell(context, column, NECraftingModuleCell.Row.UPPER_PARALLEL, grid);
            drawModuleCell(context, column, NECraftingModuleCell.Row.WORKER, grid);
            drawModuleCell(context, column, NECraftingModuleCell.Row.LOWER_PARALLEL, grid);
        }
        context.graphics.flush();
        context.disableScissor();
        drawModuleStats(context);
    }

    private void drawModuleCell(GUIContext context, int column, NECraftingModuleCell.Row row, Grid grid) {
        NECraftingModuleCell cell = moduleCellAt(column, row);
        boolean active = cell != null;
        float x = grid.x + column * grid.size;
        float y = grid.y + rowIndex(row) * grid.size;
        if (grid.size >= 10.0F) {
            drawInsetRect(context, x, y, grid.size, grid.size);
        } else {
            fillLocal(context, x, y, grid.size, grid.size, 0xFF1B1822);
        }
        float pad = grid.size >= 10.0F ? 2.0F : 1.0F;
        float inner = Math.max(1.0F, grid.size - pad * 2.0F);
        float ix = x + pad;
        float iy = y + pad;
        fillLocal(context, ix, iy, inner, inner, 0xAA17141E);
        if (active) {
            ResourceLocation base = row == NECraftingModuleCell.Row.WORKER ? CORE_SIDE : PARALLEL_FRONT;
            context.graphics.blit(base, Math.round(absX(ix)), Math.round(absY(iy)), Math.round(inner), Math.round(inner), 0, 0, 16, 16, 16, 16);
            if (row != NECraftingModuleCell.Row.WORKER) {
                context.graphics.blit(lightForTier(cell.tier()), Math.round(absX(ix)), Math.round(absY(iy)), Math.round(inner), Math.round(inner), 0, 0, 16, 16, 16, 16);
            }
        } else {
            fillLocal(context, ix + 1.0F, iy + 1.0F, Math.max(0.0F, inner - 2.0F), Math.max(0.0F, inner - 2.0F), 0x66000000);
        }
    }

    private void drawModuleStats(GUIContext context) {
        String tasks = trString("gui.neoecoae.crafting.recipe_slots", "Task Slots") + " "
                + NEHostFormat.usedTotal(snapshot.runningThreadCount(), snapshot.threadCount());
        String free = trString("gui.neoecoae.crafting.batch_parallel", "Free Parallel") + " "
                + NEHostFormat.number(snapshot.availableThreads());
        int freeWidth = Math.round(context.mc.font.width(free) * MODULE_TEXT_SCALE);
        drawScaledFittedText(context, tasks, MODULE_AREA_X + EDGE, MODULE_STATS_Y, MODULE_TEXT_SCALE,
                MODULE_AREA_W - EDGE * 2 - freeWidth - 5, TEXT_MUTED);
        drawScaledRightText(context, free, MODULE_AREA_X + MODULE_AREA_W - EDGE, MODULE_STATS_Y,
                MODULE_TEXT_SCALE, TEXT_VALUE);
        drawMiniProgressBar(context, MODULE_AREA_X + EDGE, MODULE_PROGRESS_Y, MODULE_AREA_W - EDGE * 2,
                MODULE_PROGRESS_H, snapshot.runningThreadCount(), snapshot.threadCount());
    }

    private void drawMiniProgressBar(GUIContext context, int x, int y, int w, int h, long used, long total) {
        fillLocal(context, x, y, w, h, 0xAA17141E);
        int fill = ratioWidth(used, total, w);
        if (fill > 0) {
            fillLocal(context, x, y, fill, h, TEXT_SUCCESS);
            fillLocal(context, x, y, fill, 1, 0x70FFFFFF);
        }
    }

    private static String fullNumber(long value) {
        return String.format(Locale.US, "%,d", Math.max(0L, value));
    }

    private void drawGaugeArea(GUIContext context) {
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.energy_cooling", "Energy/Coolant"), GAUGE_AREA_X + EDGE, GAUGE_AREA_Y + EDGE,
                TEXT_SCALE, GAUGE_AREA_W - EDGE * 2, TEXT_PRIMARY);
        double energyRatio = snapshot.maxEnergyUsage() <= 0 ? 0 : Math.min(1.0D, (double) snapshot.maxEnergyUsage() / ENERGY_GAUGE_REFERENCE);
        double coolantRatio = snapshot.coolant() <= 0 ? 0 : Math.min(1.0D, (double) snapshot.coolant() / ECOCraftingSystemBlockEntity.MAX_COOLANT);
        drawVerticalGauge(context, GAUGE_ENERGY_X, GAUGE_BAR_Y, GAUGE_BAR_W, GAUGE_BAR_H, energyRatio, energyRatio >= 0.9D ? TEXT_ERROR : energyRatio >= 0.5D ? TEXT_WARNING : TEXT_SUCCESS);
        drawVerticalGauge(context, GAUGE_COOLANT_X, GAUGE_BAR_Y, GAUGE_BAR_W, GAUGE_BAR_H, coolantRatio, TEXT_BLUE);
        drawScaledCenteredText(context, tr("gui.neoecoae.crafting.energy_short", "AE"), GAUGE_ENERGY_X - 7, GAUGE_BAR_Y + GAUGE_BAR_H + 2,
                GAUGE_BAR_W + 14, TEXT_SCALE, TEXT_MUTED);
        drawScaledCenteredText(context, tr("gui.neoecoae.crafting.cooling_short", "Cool"), GAUGE_COOLANT_X - 7, GAUGE_BAR_Y + GAUGE_BAR_H + 2,
                GAUGE_BAR_W + 14, TEXT_SCALE, TEXT_MUTED);
    }

    private void drawInventory(GUIContext context) {
        drawFittedText(context, tr("gui.neoecoae.common.inventory", "Inventory"),
                PLAYER_INV_X, PLAYER_INV_LABEL_Y, PLAYER_INVENTORY_WIDTH, TEXT_MUTED);
    }

    private void drawTasks(GUIContext context) {
        List<NECraftingTaskEntry> entries = snapshot.tasks();
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.tasks", "Crafting Tasks"), TASK_PANEL_X + EDGE, TASK_PANEL_Y + EDGE,
                TEXT_SCALE, TASK_PANEL_W - EDGE * 2 - 12, TEXT_PRIMARY);
        drawScaledText(context, NEHostFormat.number(entries.size()),
                TASK_PANEL_X + TASK_PANEL_W - EDGE - context.mc.font.width(NEHostFormat.number(entries.size())) * TEXT_SCALE,
                TASK_PANEL_Y + EDGE, TEXT_SCALE, TEXT_VALUE);
        if (entries.isEmpty()) {
            taskCards.resetIfEmpty(entries);
            drawScaledCenteredText(context, tr("gui.neoecoae.crafting.no_tasks", "No tasks"),
                    TASK_PANEL_X + EDGE, TASK_PANEL_Y + TASK_PANEL_H / 2.0F - 4, TASK_PANEL_W - EDGE * 2, TEXT_SCALE, TEXT_MUTED);
            return;
        }
        taskCards.draw(this, context, entries, TASK_LAYOUT, TASK_CARD_STYLE);
    }

    private void onMouseWheel(UIEvent event) {
        Grid grid = moduleGrid();
        if (containsLocal(MODULE_AREA_X, MODULE_AREA_Y, MODULE_AREA_W, MODULE_AREA_H, currentMouseX(), currentMouseY())
                && grid.contentW > MODULE_GRID_W) {
            moduleScroll.scrollBy((float) -event.deltaY * Math.max(8.0F, grid.size), grid.contentW, MODULE_GRID_W, true);
            event.stopPropagation();
            return;
        }
        taskCards.onMouseWheel(this, event, snapshot.tasks().size(), TASK_LAYOUT);
    }

    private HoverTooltips tooltipAt(double mouseX, double mouseY) {
        HoverTooltips status = statusTooltip(mouseX, mouseY);
        if (status != null) {
            return status;
        }
        HoverTooltips module = moduleTooltip(mouseX, mouseY);
        if (module != null) {
            return module;
        }
        HoverTooltips task = taskTooltip(mouseX, mouseY);
        if (task != null) {
            return task;
        }
        HoverTooltips stats = statsTooltip(mouseX, mouseY);
        if (stats != null) {
            return stats;
        }
        if (containsLocal(GAUGE_ENERGY_X, GAUGE_BAR_Y, GAUGE_BAR_W, GAUGE_BAR_H, mouseX, mouseY)) {
            return new HoverTooltips(List.of(tr("gui.neoecoae.crafting.energy_usage", "Energy Usage"),
                    Component.literal(Tooltips.ofNumber(snapshot.maxEnergyUsage()).getString() + " AE/t")), null, null, null);
        }
        if (containsLocal(GAUGE_COOLANT_X, GAUGE_BAR_Y, GAUGE_BAR_W, GAUGE_BAR_H, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                    tr("gui.neoecoae.crafting.coolant", "Coolant"),
                    tr("gui.neoecoae.crafting.coolant_amount", "Coolant: %s / %s",
                            fullNumber(snapshot.coolant()), fullNumber(ECOCraftingSystemBlockEntity.MAX_COOLANT)),
                    Component.literal(NEHostFormat.percent(snapshot.coolant(), ECOCraftingSystemBlockEntity.MAX_COOLANT))
            ), null, null, null);
        }
        return null;
    }

    private HoverTooltips moduleTooltip(double mouseX, double mouseY) {
        Grid grid = moduleGrid();
        if (grid.columns <= 0) {
            return null;
        }
        for (NECraftingModuleCell cell : snapshot.moduleCells()) {
            float x = grid.x + cell.column() * grid.size;
            float y = grid.y + rowIndex(cell.row()) * grid.size;
            float clippedX = Math.max(x, MODULE_GRID_X);
            float clippedW = Math.min(x + grid.size, MODULE_GRID_X + MODULE_GRID_W) - clippedX;
            if (clippedW <= 0.0F || !containsLocal(clippedX, y, clippedW, grid.size, mouseX, mouseY)) {
                continue;
            }
            if (cell.row() == NECraftingModuleCell.Row.WORKER) {
                ItemStack output = workerOutputAt(cell.column());
                if (!output.isEmpty()) {
                    List<Component> lines = itemTooltip(output);
                    lines.add(Component.translatable("block.neoecoae.crafting_worker").withStyle(ChatFormatting.GRAY));
                    lines.add(Component.literal(modulePos(cell)).withStyle(ChatFormatting.GRAY));
                    return new HoverTooltips(lines, output.getTooltipImage().orElse(null), null, output);
                }
            }
            Component name = cell.row() == NECraftingModuleCell.Row.WORKER
                    ? Component.translatable("block.neoecoae.crafting_worker")
                    : Component.translatable(parallelCoreNameKey(cell.tier()));
            List<Component> lines = new ArrayList<>();
            lines.add(name);
            if (cell.row() != NECraftingModuleCell.Row.WORKER) {
                lines.add(Component.translatable("gui.neoecoae.crafting.parallel_per_core",
                        NEHostFormat.number(parallelPerCore(cell.tier(), snapshot.overclocked()))));
            }
            lines.add(Component.literal(modulePos(cell)).withStyle(ChatFormatting.GRAY));
            return new HoverTooltips(lines, null, null, null);
        }
        return null;
    }

    private ItemStack workerOutputAt(int column) {
        if (column < 0 || column >= snapshot.workerOutputs().size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = snapshot.workerOutputs().get(column);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private HoverTooltips statusTooltip(double mouseX, double mouseY) {
        if (containsLocal(STATUS_LIGHT_X, STATUS_ROW_0_Y - 4, STATUS_AREA_W - EDGE * 2, STATUS_LIGHT_SIZE, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                    Component.translatable(snapshot.overclocked()
                            ? "gui.neoecoae.crafting.overclock.on"
                            : "gui.neoecoae.crafting.overclock.off"),
                    Component.translatable("gui.neoecoae.crafting.overclocked.tooltip")
            ), null, null, null);
        }
        if (containsLocal(STATUS_LIGHT_X, STATUS_ROW_1_Y - 4, STATUS_AREA_W - EDGE * 2, STATUS_LIGHT_SIZE, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                    Component.translatable(snapshot.activeCooling()
                            ? "gui.neoecoae.crafting.active_cooling.on"
                            : "gui.neoecoae.crafting.active_cooling.off"),
                    Component.translatable("gui.neoecoae.crafting.active_cooling.tooltip")
            ), null, null, null);
        }
        if (containsLocal(STATUS_LIGHT_X, STATUS_ROW_2_Y - 4, STATUS_AREA_W - EDGE * 2, STATUS_LIGHT_SIZE, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                    tr("gui.neoecoae.crafting.coolant", "Coolant"),
                    tr("gui.neoecoae.crafting.coolant_amount", "Coolant: %s / %s",
                            fullNumber(snapshot.coolant()), fullNumber(ECOCraftingSystemBlockEntity.MAX_COOLANT)),
                    Component.literal(NEHostFormat.percent(snapshot.coolant(), ECOCraftingSystemBlockEntity.MAX_COOLANT))
            ), null, null, null);
        }
        return null;
    }

    private HoverTooltips taskTooltip(double mouseX, double mouseY) {
        return taskCards.tooltipAt(this, snapshot.tasks(), TASK_LAYOUT, mouseX, mouseY);
    }

    private HoverTooltips statsTooltip(double mouseX, double mouseY) {
        boolean titleHit = containsLocal(MODULE_AREA_X + EDGE, MODULE_AREA_Y + EDGE,
                MODULE_AREA_W - EDGE * 2, 10, mouseX, mouseY);
        boolean statsHit = containsLocal(MODULE_AREA_X + EDGE, MODULE_STATS_Y - 1,
                MODULE_AREA_W - EDGE * 2, MODULE_AREA_Y + MODULE_AREA_H - EDGE - MODULE_STATS_Y + 1, mouseX, mouseY);
        if (!titleHit && !statsHit) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.neoecoae.crafting.parallel_core_tiers"));
        lines.add(Component.literal("FT4: " + countTier(1) + " x " + parallelPerCore(1, snapshot.overclocked())));
        lines.add(Component.literal("FT6: " + countTier(2) + " x " + parallelPerCore(2, snapshot.overclocked())));
        lines.add(Component.literal("FT9: " + countTier(3) + " x " + parallelPerCore(3, snapshot.overclocked())));
        lines.add(Component.translatable("gui.neoecoae.crafting.worker_count", snapshot.workerCount()));
        lines.add(Component.translatable("gui.neoecoae.crafting.pattern_bus_count", snapshot.patternBusCount()));
        lines.add(Component.translatable("gui.neoecoae.crafting.recipe_slots")
                .append(": ")
                .append(Component.literal(fullNumber(snapshot.runningThreadCount()) + " / " + fullNumber(snapshot.threadCount()))));
        lines.add(Component.translatable("gui.neoecoae.crafting.batch_parallel")
                .append(": ")
                .append(Component.literal(fullNumber(snapshot.availableThreads()))));
        return new HoverTooltips(lines, null, null, null);
    }

    private Grid moduleGrid() {
        int maxColumn = -1;
        for (NECraftingModuleCell cell : snapshot.moduleCells()) {
            maxColumn = Math.max(maxColumn, cell.column());
        }
        int columns = Math.max(maxColumn + 1, snapshot.workerCount());
        if (columns <= 0) {
            moduleScroll.update(0.0F, MODULE_GRID_W);
            return new Grid(MODULE_GRID_X, MODULE_GRID_Y, 0, 18.0F, 0.0F);
        }
        float size = Math.min(18.0F, Math.max(6.0F, Math.min((float) MODULE_GRID_W / columns, MODULE_GRID_H / 3.0F)));
        float contentW = columns * size;
        moduleScroll.update(contentW, MODULE_GRID_W);
        float x = contentW <= MODULE_GRID_W
                ? MODULE_GRID_X + Math.max(0.0F, (MODULE_GRID_W - contentW) / 2.0F)
                : MODULE_GRID_X - moduleScroll.offset();
        float y = MODULE_GRID_Y + Math.max(0.0F, (MODULE_GRID_H - size * 3.0F) / 2.0F);
        return new Grid(x, y, columns, size, contentW);
    }

    private NECraftingModuleCell moduleCellAt(int column, NECraftingModuleCell.Row row) {
        for (NECraftingModuleCell cell : snapshot.moduleCells()) {
            if (cell.column() == column && cell.row() == row) {
                return cell;
            }
        }
        return null;
    }

    private int countTier(int tier) {
        int count = 0;
        for (NECraftingModuleCell cell : snapshot.moduleCells()) {
            if (cell.row() != NECraftingModuleCell.Row.WORKER && cell.tier() == tier) {
                count++;
            }
        }
        return count;
    }

    private static int parallelPerCore(int tier, boolean overclocked) {
        ECOTier ecoTier = ecoTier(tier);
        return ecoTier.getCrafterParallel() + (overclocked ? ecoTier.getOverclockedCrafterParallel() : 0);
    }

    private static ECOTier ecoTier(int tier) {
        return switch (tier) {
            case 3 -> ECOTier.L9;
            case 2 -> ECOTier.L6;
            default -> ECOTier.L4;
        };
    }

    private static String modulePos(NECraftingModuleCell cell) {
        return "x=" + cell.pos().getX() + ", y=" + cell.pos().getY() + ", z=" + cell.pos().getZ();
    }

    private static int rowIndex(NECraftingModuleCell.Row row) {
        return switch (row) {
            case UPPER_PARALLEL -> 0;
            case WORKER -> 1;
            case LOWER_PARALLEL -> 2;
        };
    }

    private static ResourceLocation lightForTier(int tier) {
        return switch (tier) {
            case 3 -> LIGHT_L9;
            case 2 -> LIGHT_L6;
            default -> LIGHT_L4;
        };
    }

    private static String parallelCoreNameKey(int tier) {
        return switch (tier) {
            case 3 -> "block.neoecoae.crafting_parallel_core_l9";
            case 2 -> "block.neoecoae.crafting_parallel_core_l6";
            default -> "block.neoecoae.crafting_parallel_core_l4";
        };
    }

    private static float approach(float current, float target) {
        float next = Mth.lerp(0.16F, current, target);
        return Math.abs(next - target) < 0.05F ? target : next;
    }

    private static final class ScrollModel {
        private float offset;
        private float target;
        private float max;

        void update(float contentSize, float viewportSize) {
            max = Math.max(0.0F, contentSize - viewportSize);
            target = Mth.clamp(target, 0.0F, max);
            offset = max <= 0.0F ? 0.0F : approach(offset, target);
            if (max <= 0.0F) {
                target = 0.0F;
            }
        }

        void scrollBy(float delta, float contentSize, float viewportSize, boolean snap) {
            update(contentSize, viewportSize);
            target = Mth.clamp(target + delta, 0.0F, max);
            if (snap) {
                offset = target;
            }
        }

        float offset() {
            return offset;
        }

        float max() {
            return max;
        }
    }

    private record Grid(float x, float y, int columns, float size, float contentW) {
    }

    private record CraftingSnapshot(
        boolean formed,
        boolean active,
        int workerCount,
        int parallelCount,
        int patternBusCount,
        int runningThreadCount,
        int threadCount,
        int availableThreads,
        boolean overclocked,
        boolean activeCooling,
        int coolant,
        long maxEnergyUsage,
        List<NECraftingTaskEntry> tasks,
        List<NECraftingModuleCell> moduleCells,
        List<ItemStack> workerOutputs
    ) {
        private static final CraftingSnapshot EMPTY = new CraftingSnapshot(
            false, false, 0, 0, 0, 0, 0, 0, false, false, 0, 0L, List.of(), List.of(), List.of()
        );
    }
}
