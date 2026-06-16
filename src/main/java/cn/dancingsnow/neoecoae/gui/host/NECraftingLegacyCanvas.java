package cn.dancingsnow.neoecoae.gui.host;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class NECraftingLegacyCanvas extends NEHostCanvas {
    static final int UI_WIDTH = 304;
    static final int UI_HEIGHT = 268;
    private static final float TEXT_SCALE = 0.8F;
    private static final long ENERGY_GAUGE_REFERENCE = 1_000_000_000L;
    private static final ResourceLocation CORE_SIDE = NeoECOAE.id("textures/block/crafting/core/core_side.png");
    private static final ResourceLocation PARALLEL_FRONT = NeoECOAE.id("textures/block/crafting/core/parallel_core_north.png");
    private static final ResourceLocation LIGHT_L4 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_a.png");
    private static final ResourceLocation LIGHT_L6 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_b.png");
    private static final ResourceLocation LIGHT_L9 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_c.png");

    private static final int PANEL_MARGIN = 6;
    private static final int MAIN_PANEL_X = PANEL_MARGIN;
    private static final int MAIN_PANEL_Y = 20;
    private static final int MAIN_PANEL_W = UI_WIDTH - PANEL_MARGIN * 2;
    private static final int MAIN_PANEL_H = 151;
    static final int TOOLBAR_BUTTON_SIZE = 14;
    static final int TOOLBAR_BUTTON_STRIDE = TOOLBAR_BUTTON_SIZE + 3;
    static final int TOOLBAR_X = UI_WIDTH - PANEL_MARGIN - TOOLBAR_BUTTON_SIZE * 3 - 3 * 2;
    static final int TOOLBAR_Y = 4;

    private static final int MODULE_AREA_X = MAIN_PANEL_X + 6;
    private static final int MODULE_AREA_Y = MAIN_PANEL_Y + 6;
    private static final int MODULE_AREA_W = MAIN_PANEL_W - 12;
    private static final int MODULE_AREA_H = 62;
    private static final int MODULE_GRID_X = MODULE_AREA_X + 6;
    private static final int MODULE_GRID_Y = MODULE_AREA_Y + 14;
    private static final int MODULE_GRID_W = MODULE_AREA_W - 12;
    private static final int MODULE_GRID_H = MODULE_AREA_H - 18;
    private static final int MIDDLE_AREA_Y = MODULE_AREA_Y + MODULE_AREA_H + 6;
    private static final int STATUS_AREA_X = MODULE_AREA_X;
    private static final int STATUS_AREA_Y = MIDDLE_AREA_Y;
    private static final int STATUS_AREA_W = 64;
    private static final int STATUS_AREA_H = 70;
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
    static final int PLAYER_INV_X = MODULE_AREA_X;
    private static final int PLAYER_INV_LABEL_Y = MAIN_PANEL_Y + MAIN_PANEL_H + 6;
    static final int PLAYER_INV_Y = PLAYER_INV_LABEL_Y + 10;
    static final int PLAYER_HOTBAR_Y = PLAYER_INV_Y + 18 * 3 + 2;
    private static final int TASK_PANEL_GAP = 8;
    private static final int TASK_PANEL_X = PLAYER_INV_X + 18 * 9 + TASK_PANEL_GAP;
    private static final int TASK_PANEL_Y = PLAYER_INV_LABEL_Y - 2;
    private static final int TASK_PANEL_W = UI_WIDTH - TASK_PANEL_X - PANEL_MARGIN;
    private static final int TASK_PANEL_H = PLAYER_HOTBAR_Y + 18 - TASK_PANEL_Y;
    private static final int TASK_CARD_X = TASK_PANEL_X + 8;
    private static final int TASK_CARD_Y = TASK_PANEL_Y + 19;
    private static final int TASK_CARD_W = TASK_PANEL_W - 16;
    private static final int TASK_CARD_H = 16;
    private static final int TASK_CARD_STRIDE = 18;
    private static final int TASK_LIST_BOTTOM_Y = TASK_PANEL_Y + TASK_PANEL_H - 1;

    private final ECOCraftingSystemBlockEntity crafting;
    private final NEAnimatedTaskCards taskCards = new NEAnimatedTaskCards();
    private int taskScrollOffset;
    private CraftingSnapshot snapshot = CraftingSnapshot.EMPTY;

    NECraftingLegacyCanvas(ECOCraftingSystemBlockEntity crafting) {
        super(UI_WIDTH, UI_HEIGHT);
        this.crafting = crafting;
        bindSnapshot();
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            HoverTooltips tooltip = tooltipAt(event.x, event.y);
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
            NEHostSnapshots.readModuleCells(buf)
        ));
    }

    @Override
    protected void drawHostBackground(GUIContext context) {
        drawInsetRect(context, MAIN_PANEL_X, MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);
        drawInsetRect(context, MODULE_AREA_X, MODULE_AREA_Y, MODULE_AREA_W, MODULE_AREA_H);
        drawInsetRect(context, STATUS_AREA_X, STATUS_AREA_Y, STATUS_AREA_W, STATUS_AREA_H);
        drawInsetRect(context, STATS_AREA_X, STATS_AREA_Y, STATS_AREA_W, STATS_AREA_H);
        drawInsetRect(context, GAUGE_AREA_X, GAUGE_AREA_Y, GAUGE_AREA_W, GAUGE_AREA_H);
        drawInsetRect(context, TASK_PANEL_X, TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H);
        drawToolbar(context);
        drawHeader(context);
        drawModulePreview(context);
        drawStatusArea(context);
        drawStatsArea(context);
        drawGaugeArea(context);
        drawInventory(context);
        drawTasks(context);
    }

    private void drawHeader(GUIContext context) {
        Component formedLabel = tr("gui.neoecoae.machine.formed", "Formed").append(": ");
        Component formedValue = boolText(snapshot.formed());
        Component activeLabel = Component.literal("  ").append(tr("gui.neoecoae.machine.active", "Active")).append(": ");
        Component activeValue = boolText(snapshot.active());
        float right = TOOLBAR_X - 8;
        int width = Math.round((context.mc.font.width(formedLabel) + context.mc.font.width(formedValue)
                + context.mc.font.width(activeLabel) + context.mc.font.width(activeValue)) * TEXT_SCALE);
        float x = Math.max(8, right - width);
        drawFittedText(context, crafting.getHostTitle(), 8, 8, Math.max(40, Math.round(x) - 12), TEXT_PRIMARY);
        drawScaledText(context, formedLabel, x, 8, TEXT_SCALE, 0xFF5D5D5D);
        x += context.mc.font.width(formedLabel) * TEXT_SCALE;
        drawScaledText(context, formedValue, x, 8, TEXT_SCALE, snapshot.formed() ? 0xFF00A850 : 0xFFC03434);
        x += context.mc.font.width(formedValue) * TEXT_SCALE;
        drawScaledText(context, activeLabel, x, 8, TEXT_SCALE, 0xFF5D5D5D);
        x += context.mc.font.width(activeLabel) * TEXT_SCALE;
        drawScaledText(context, activeValue, x, 8, TEXT_SCALE, snapshot.active() ? 0xFF00A850 : 0xFF606060);
    }

    private void drawToolbar(GUIContext context) {
        for (int i = 0; i < 3; i++) {
            boolean hovered = containsLocal(TOOLBAR_X + i * TOOLBAR_BUTTON_STRIDE, TOOLBAR_Y, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE, context.mouseX, context.mouseY);
            drawToolbarButton(context, TOOLBAR_X + i * TOOLBAR_BUTTON_STRIDE, TOOLBAR_Y, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE, hovered);
        }
        drawToolbarIcon(context, 0, snapshot.overclocked() ? NEAeSprite.LEVEL_ENERGY : NEAeSprite.POWER_UNIT_AE);
        drawToolbarIcon(context, 1, snapshot.activeCooling() ? NEAeSprite.FLUID_SUBSTITUTION_ENABLED : NEAeSprite.FLUID_SUBSTITUTION_DISABLED);
        drawToolbarIcon(context, 2, NEAeSprite.CONDENSER_OUTPUT_TRASH);
    }

    private void drawToolbarIcon(GUIContext context, int index, NEAeSprite icon) {
        drawIcon(context, icon,
                TOOLBAR_X + index * TOOLBAR_BUTTON_STRIDE + (TOOLBAR_BUTTON_SIZE - icon.width()) / 2.0F,
                TOOLBAR_Y + (TOOLBAR_BUTTON_SIZE - icon.height()) / 2.0F);
    }

    private void drawModulePreview(GUIContext context) {
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.module_preview", "Structure Preview"),
                MODULE_AREA_X + 8, MODULE_AREA_Y + 5, TEXT_SCALE, MODULE_AREA_W - 74, TEXT_PRIMARY);
        String moduleCounts = "FT " + NEHostFormat.number(snapshot.parallelCount()) + "   FX " + NEHostFormat.number(snapshot.workerCount());
        drawScaledRightText(context, moduleCounts, MODULE_AREA_X + MODULE_AREA_W - 8, MODULE_AREA_Y + 5, TEXT_SCALE, TEXT_VALUE);
        Grid grid = moduleGrid();
        if (grid.columns <= 0) {
            drawCenteredText(context, tr("gui.neoecoae.crafting.no_worker_cores", "No worker cores"), MODULE_AREA_X, MODULE_AREA_Y + 39, MODULE_AREA_W, TEXT_MUTED);
            return;
        }
        for (int column = 0; column < grid.columns; column++) {
            drawModuleCell(context, column, NECraftingModuleCell.Row.UPPER_PARALLEL, grid);
            drawModuleCell(context, column, NECraftingModuleCell.Row.WORKER, grid);
            drawModuleCell(context, column, NECraftingModuleCell.Row.LOWER_PARALLEL, grid);
        }
    }

    private void drawModuleCell(GUIContext context, int column, NECraftingModuleCell.Row row, Grid grid) {
        NECraftingModuleCell cell = moduleCellAt(column, row);
        boolean active = cell != null;
        float x = grid.x + column * grid.size;
        float y = grid.y + rowIndex(row) * grid.size;
        if (grid.size >= 10) {
            drawInsetRect(context, x, y, grid.size, grid.size);
        } else {
            fillLocal(context, x, y, grid.size, grid.size, 0xFF1B1822);
        }
        float pad = grid.size >= 10 ? 2 : 1;
        float inner = Math.max(1, grid.size - pad * 2);
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
            fillLocal(context, ix + 1, iy + 1, Math.max(0, inner - 2), Math.max(0, inner - 2), 0x66000000);
        }
    }

    private void drawStatusArea(GUIContext context) {
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.status", "Status"), STATUS_AREA_X + 8, STATUS_AREA_Y + 5,
                TEXT_SCALE, STATUS_AREA_W - 16, TEXT_PRIMARY);
        drawStatusRow(context, tr("gui.neoecoae.crafting.overclock", "OC"), snapshot.overclocked(), STATUS_AREA_Y + 21);
        drawStatusRow(context, tr("gui.neoecoae.crafting.cooling_short", "Cool"), snapshot.activeCooling(), STATUS_AREA_Y + 36);
        drawStatusRow(context, tr("gui.neoecoae.crafting.coolant_cache_short", "Cache"), snapshot.coolant() > 0, STATUS_AREA_Y + 51);
    }

    private void drawStatusRow(GUIContext context, Component label, boolean enabled, int y) {
        drawInsetRect(context, STATUS_AREA_X + 4, y - 3, 13, 13);
        fillLocal(context, STATUS_AREA_X + 8, y + 1, 5, 5, enabled ? TEXT_SUCCESS : TEXT_ERROR);
        String value = trString(enabled ? "gui.neoecoae.common.on" : "gui.neoecoae.common.off", enabled ? "On" : "Off");
        int valueWidth = Math.round(context.mc.font.width(value) * TEXT_SCALE);
        int labelMax = STATUS_AREA_W - 30 - valueWidth;
        drawScaledFittedText(context, label, STATUS_AREA_X + 22, y, TEXT_SCALE, labelMax, TEXT_MUTED);
        drawScaledText(context, value, STATUS_AREA_X + STATUS_AREA_W - 6 - valueWidth, y, TEXT_SCALE,
                enabled ? TEXT_SUCCESS : TEXT_ERROR);
    }

    private void drawStatsArea(GUIContext context) {
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.stats", "Stats"), STATS_AREA_X + 8, STATS_AREA_Y + 5,
                TEXT_SCALE, STATS_AREA_W - 16, TEXT_PRIMARY);
        int x = STATS_AREA_X + 8;
        int y = STATS_AREA_Y + 19;
        drawScaledPairLine(context, trString("gui.neoecoae.crafting.recipe_slots", "Tasks") + ": ",
                snapshot.runningThreadCount(), snapshot.threadCount(), x, y);
        drawProgressBar(context, x, STATS_AREA_Y + 31, STATS_AREA_W - 16, 9, snapshot.runningThreadCount(), snapshot.threadCount(), TEXT_SUCCESS);
        y += 25;
        drawScaledValueLine(context, trString("gui.neoecoae.crafting.batch_parallel", "Free") + ": ",
                snapshot.availableThreads(), x, y, STATS_AREA_W - 16);
        y += 11;
        drawScaledValueLine(context, trString("gui.neoecoae.host.crafting.pattern_buses", "Buses") + ": ",
                snapshot.patternBusCount(), x, y, 58);
        drawScaledValueLine(context, trString("gui.neoecoae.host.crafting.parallel_cores", "Cores") + ": ",
                snapshot.parallelCount(), x + 62, y, STATS_AREA_W - 78);
    }

    private void drawScaledValueLine(GUIContext context, String label, long value, int x, int y, int maxWidth) {
        String valueText = NEHostFormat.number(value);
        int valueWidth = Math.round(context.mc.font.width(valueText) * TEXT_SCALE);
        drawScaledFittedText(context, label, x, y, TEXT_SCALE, Math.max(0, maxWidth - valueWidth), TEXT_MUTED);
        drawScaledText(context, valueText, x + maxWidth - valueWidth, y, TEXT_SCALE, TEXT_VALUE);
    }

    private void drawScaledPairLine(GUIContext context, String label, long used, long total, int x, int y) {
        float cursor = x;
        drawScaledText(context, label, cursor, y, TEXT_SCALE, TEXT_MUTED);
        cursor += context.mc.font.width(label) * TEXT_SCALE;
        drawScaledText(context, NEHostFormat.number(used), cursor, y, TEXT_SCALE, TEXT_SUCCESS);
        cursor += context.mc.font.width(NEHostFormat.number(used)) * TEXT_SCALE;
        drawScaledText(context, " / ", cursor, y, TEXT_SCALE, TEXT_MUTED);
        cursor += context.mc.font.width(" / ") * TEXT_SCALE;
        drawScaledText(context, NEHostFormat.number(total), cursor, y, TEXT_SCALE, TEXT_VALUE);
    }

    private void drawGaugeArea(GUIContext context) {
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.energy_cooling", "Energy/Coolant"), GAUGE_AREA_X + 8, GAUGE_AREA_Y + 5,
                TEXT_SCALE, GAUGE_AREA_W - 16, TEXT_PRIMARY);
        int energyX = GAUGE_AREA_X + 8;
        int coolantX = GAUGE_AREA_X + GAUGE_AREA_W - 8 - GAUGE_BAR_W;
        double energyRatio = snapshot.maxEnergyUsage() <= 0 ? 0 : Math.min(1.0D, (double) snapshot.maxEnergyUsage() / ENERGY_GAUGE_REFERENCE);
        double coolantRatio = snapshot.coolant() <= 0 ? 0 : Math.min(1.0D, (double) snapshot.coolant() / ECOCraftingSystemBlockEntity.MAX_COOLANT);
        drawVerticalGauge(context, energyX, GAUGE_BAR_Y, GAUGE_BAR_W, GAUGE_BAR_H, energyRatio, energyRatio >= 0.9D ? TEXT_ERROR : energyRatio >= 0.5D ? TEXT_WARNING : TEXT_SUCCESS);
        drawVerticalGauge(context, coolantX, GAUGE_BAR_Y, GAUGE_BAR_W, GAUGE_BAR_H, coolantRatio, TEXT_BLUE);
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.energy_short", "AE"), energyX - 8, GAUGE_BAR_Y + GAUGE_BAR_H + 1,
                TEXT_SCALE, 32, TEXT_MUTED);
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.cooling_short", "Cool"), coolantX - 8, GAUGE_BAR_Y + GAUGE_BAR_H + 1,
                TEXT_SCALE, 32, TEXT_MUTED);
    }

    private void drawInventory(GUIContext context) {
        drawFittedText(context, tr("gui.neoecoae.common.inventory", "Inventory"), PLAYER_INV_X, PLAYER_INV_LABEL_Y, 18 * 9, TEXT_MUTED);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(context, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(context, PLAYER_INV_X + col * 18, PLAYER_HOTBAR_Y);
        }
    }

    private void drawTasks(GUIContext context) {
        List<NECraftingTaskEntry> entries = snapshot.tasks();
        taskScrollOffset = clampTaskScroll(taskScrollOffset, entries.size());
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.tasks", "Crafting Tasks"), TASK_PANEL_X + 8, TASK_PANEL_Y + 5,
                TEXT_SCALE, TASK_PANEL_W - 28, TEXT_PRIMARY);
        drawScaledText(context, NEHostFormat.number(entries.size()),
                TASK_PANEL_X + TASK_PANEL_W - 8 - context.mc.font.width(NEHostFormat.number(entries.size())) * TEXT_SCALE,
                TASK_PANEL_Y + 5, TEXT_SCALE, TEXT_VALUE);
        if (entries.isEmpty()) {
            drawCenteredText(context, tr("gui.neoecoae.crafting.no_tasks", "No tasks"), TASK_PANEL_X + 6, TASK_PANEL_Y + 42, TASK_PANEL_W - 12, TEXT_MUTED);
            return;
        }
        context.graphics.flush();
        context.enableScissor(absX(TASK_CARD_X), absY(TASK_CARD_Y), TASK_CARD_W, TASK_LIST_BOTTOM_Y - TASK_CARD_Y + 1);
        for (NEAnimatedTaskCards.Frame frame : taskCards.update(entries, taskScrollOffset, visibleRows(), TASK_CARD_Y, TASK_CARD_STRIDE)) {
            drawTaskCard(context, frame.entry(), frame.y(), frame.alpha());
        }
        context.graphics.flush();
        context.disableScissor();
        drawTaskScrollbar(context, entries.size());
    }

    private void drawTaskCard(GUIContext context, NECraftingTaskEntry entry, float y, float alpha) {
        int color = statusColor(entry.status());
        fillLocal(context, TASK_CARD_X, y, TASK_CARD_W, TASK_CARD_H, withAlpha(0xFFD8D3E4, alpha));
        fillLocal(context, TASK_CARD_X + 1, y + 1, TASK_CARD_W - 2, TASK_CARD_H - 2, withAlpha(0xFF121016, alpha));
        fillLocal(context, TASK_CARD_X + 2, y + 2, TASK_CARD_W - 4, TASK_CARD_H - 4, withAlpha(0xFF4D4855, alpha));
        fillLocal(context, TASK_CARD_X + 3, y + 3, TASK_CARD_W - 6, TASK_CARD_H - 6, withAlpha(0xFF2C2735, alpha));
        fillLocal(context, TASK_CARD_X + 3, y + TASK_CARD_H - 3, TASK_CARD_W - 6, 1, withAlpha(color, alpha));
        drawItem(context, entry.output(), TASK_CARD_X + 1, y);
        String amount = "x" + NEHostFormat.number(entry.outputAmount());
        int amountW = Math.round(context.mc.font.width(amount) * TEXT_SCALE);
        String name = NEHostDraw.fit(context, entry.output().getHoverName().getString(), Math.max(16, TASK_CARD_W - 28 - amountW));
        drawScaledText(context, name, TASK_CARD_X + 20, y + 4, TEXT_SCALE, withAlpha(TEXT_PRIMARY, alpha));
        drawScaledText(context, amount, TASK_CARD_X + TASK_CARD_W - 5 - amountW, y + 4, TEXT_SCALE, withAlpha(TEXT_VALUE, alpha));
        long done = Math.max(0L, entry.totalTicks() - entry.remainingTicks());
        int fill = entry.status() == NECraftingTaskEntry.Status.WAITING_OUTPUT ? TASK_CARD_W - 25 : ratioWidth(done, entry.totalTicks(), TASK_CARD_W - 25);
        if (entry.status() == NECraftingTaskEntry.Status.QUEUED) {
            fill = 1;
        }
        fillLocal(context, TASK_CARD_X + 20, y + TASK_CARD_H - 4, TASK_CARD_W - 25, 2, withAlpha(0xAA17141E, alpha));
        if (fill > 0) {
            fillLocal(context, TASK_CARD_X + 20, y + TASK_CARD_H - 4, fill, 2, withAlpha(color, alpha));
        }
    }

    private void drawTaskScrollbar(GUIContext context, int total) {
        int visible = visibleRows();
        if (total <= visible) {
            return;
        }
        float height = TASK_LIST_BOTTOM_Y - TASK_CARD_Y - 1;
        float thumbH = Math.max(10.0F, height * visible / total);
        float thumbY = TASK_CARD_Y + (height - thumbH) * taskScrollOffset / Math.max(1.0F, total - visible);
        drawScroller(context, TASK_PANEL_X + TASK_PANEL_W - 5, TASK_CARD_Y, 3, height, thumbY, thumbH);
    }

    private void onMouseWheel(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
        List<NECraftingTaskEntry> entries = snapshot.tasks();
        if (containsLocal(TASK_PANEL_X, TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H, event.x, event.y)
                && entries.size() > visibleRows()) {
            taskScrollOffset = clampTaskScroll(taskScrollOffset + (event.deltaY < 0 ? 1 : -1), entries.size());
            event.stopPropagation();
        }
    }

    private HoverTooltips tooltipAt(double mouseX, double mouseY) {
        HoverTooltips toolbar = toolbarTooltip(mouseX, mouseY);
        if (toolbar != null) {
            return toolbar;
        }
        HoverTooltips module = moduleTooltip(mouseX, mouseY);
        if (module != null) {
            return module;
        }
        HoverTooltips task = taskTooltip(mouseX, mouseY);
        if (task != null) {
            return task;
        }
        if (containsLocal(GAUGE_AREA_X + 8, GAUGE_BAR_Y, GAUGE_BAR_W, GAUGE_BAR_H, mouseX, mouseY)) {
            return new HoverTooltips(List.of(tr("gui.neoecoae.crafting.energy_usage", "Energy Usage"),
                    Component.literal(Tooltips.ofNumber(snapshot.maxEnergyUsage()).getString() + " AE")), null, null, null);
        }
        if (containsLocal(GAUGE_AREA_X + GAUGE_AREA_W - 8 - GAUGE_BAR_W, GAUGE_BAR_Y, GAUGE_BAR_W, GAUGE_BAR_H, mouseX, mouseY)) {
            return new HoverTooltips(List.of(tr("gui.neoecoae.host.crafting.coolant", "Coolant"),
                    Component.literal(NEHostFormat.usedTotal(snapshot.coolant(), ECOCraftingSystemBlockEntity.MAX_COOLANT) + " mB")), null, null, null);
        }
        return null;
    }

    private HoverTooltips toolbarTooltip(double mouseX, double mouseY) {
        if (containsLocal(TOOLBAR_X, TOOLBAR_Y, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE, mouseX, mouseY)) {
            return new HoverTooltips(List.of(Component.translatable(snapshot.overclocked()
                    ? "gui.neoecoae.crafting.overclock.on"
                    : "gui.neoecoae.crafting.overclock.off")), null, null, null);
        }
        if (containsLocal(TOOLBAR_X + TOOLBAR_BUTTON_STRIDE, TOOLBAR_Y, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE, mouseX, mouseY)) {
            return new HoverTooltips(List.of(Component.translatable(snapshot.activeCooling()
                    ? "gui.neoecoae.crafting.active_cooling.on"
                    : "gui.neoecoae.crafting.active_cooling.off")), null, null, null);
        }
        if (containsLocal(TOOLBAR_X + TOOLBAR_BUTTON_STRIDE * 2, TOOLBAR_Y, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                    Component.translatable("gui.neoecoae.crafting.clear_coolant"),
                    Component.translatable("gui.neoecoae.crafting.clear_coolant.tooltip")
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
            if (!containsLocal(x, y, grid.size, grid.size, mouseX, mouseY)) {
                continue;
            }
            Component name = cell.row() == NECraftingModuleCell.Row.WORKER
                    ? Component.translatable("block.neoecoae.crafting_worker")
                    : Component.translatable(parallelCoreNameKey(cell.tier()));
            return new HoverTooltips(List.of(name,
                    Component.literal("x=" + cell.pos().getX() + ", y=" + cell.pos().getY() + ", z=" + cell.pos().getZ()).withStyle(ChatFormatting.GRAY)), null, null, null);
        }
        return null;
    }

    private HoverTooltips taskTooltip(double mouseX, double mouseY) {
        List<NECraftingTaskEntry> entries = snapshot.tasks();
        taskScrollOffset = clampTaskScroll(taskScrollOffset, entries.size());
        int visible = Math.min(visibleRows(), entries.size() - taskScrollOffset);
        for (int i = 0; i < visible; i++) {
            int y = TASK_CARD_Y + i * TASK_CARD_STRIDE;
            if (!containsLocal(TASK_CARD_X, y, TASK_CARD_W, TASK_CARD_H, mouseX, mouseY)) {
                continue;
            }
            NECraftingTaskEntry entry = entries.get(taskScrollOffset + i);
            List<Component> lines = new ArrayList<>();
            lines.add(entry.output().getHoverName());
            lines.add(Component.translatable(statusKey(entry.status())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.neoecoae.crafting.task.amount", NEHostFormat.number(entry.outputAmount())));
            lines.add(Component.translatable("gui.neoecoae.crafting.task.crafts", NEHostFormat.number(entry.craftCount())));
            if (entry.totalTicks() > 0L) {
                long done = Math.max(0L, entry.totalTicks() - entry.remainingTicks());
                lines.add(Component.literal(NEHostFormat.percent(done, entry.totalTicks())).withStyle(ChatFormatting.AQUA));
            }
            return new HoverTooltips(lines, entry.output().getTooltipImage().orElse(null), null, entry.output());
        }
        return null;
    }

    private int visibleRows() {
        int space = TASK_LIST_BOTTOM_Y - TASK_CARD_Y;
        return Math.max(1, 1 + Math.max(0, space - TASK_CARD_H) / TASK_CARD_STRIDE);
    }

    private int clampTaskScroll(int value, int total) {
        return Mth.clamp(value, 0, Math.max(0, total - visibleRows()));
    }

    private Grid moduleGrid() {
        int maxColumn = -1;
        for (NECraftingModuleCell cell : snapshot.moduleCells()) {
            maxColumn = Math.max(maxColumn, cell.column());
        }
        int columns = Math.max(maxColumn + 1, snapshot.workerCount());
        if (columns <= 0) {
            return new Grid(MODULE_GRID_X, MODULE_GRID_Y, 0, 18);
        }
        float size = Math.min(18, Math.max(6, Math.min((float) MODULE_GRID_W / columns, MODULE_GRID_H / 3.0F)));
        float totalW = columns * size;
        float x = MODULE_GRID_X + Math.max(0.0F, (MODULE_GRID_W - totalW) / 2.0F);
        float y = MODULE_GRID_Y + Math.max(0.0F, (MODULE_GRID_H - size * 3.0F) / 2.0F);
        return new Grid(x, y, columns, size);
    }

    private NECraftingModuleCell moduleCellAt(int column, NECraftingModuleCell.Row row) {
        for (NECraftingModuleCell cell : snapshot.moduleCells()) {
            if (cell.column() == column && cell.row() == row) {
                return cell;
            }
        }
        return null;
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

    private static int statusColor(NECraftingTaskEntry.Status status) {
        return switch (status) {
            case RUNNING -> TEXT_SUCCESS;
            case QUEUED -> TEXT_WARNING;
            case WAITING_OUTPUT -> TEXT_BLUE;
        };
    }

    private static String statusKey(NECraftingTaskEntry.Status status) {
        return switch (status) {
            case RUNNING -> "gui.neoecoae.crafting.task.status.running";
            case QUEUED -> "gui.neoecoae.crafting.task.status.queued";
            case WAITING_OUTPUT -> "gui.neoecoae.crafting.task.status.waiting_output";
        };
    }

    private record Grid(float x, float y, int columns, float size) {
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
        List<NECraftingModuleCell> moduleCells
    ) {
        private static final CraftingSnapshot EMPTY = new CraftingSnapshot(
            false, false, 0, 0, 0, 0, 0, 0, false, false, 0, 0L, List.of(), List.of()
        );
    }
}
