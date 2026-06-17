package cn.dancingsnow.neoecoae.gui.host;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.network.chat.Component;

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
    private static final float TASK_ITEM_SCALE = 0.95F;
    private static final long ENERGY_GAUGE_REFERENCE = 1_000_000_000L;

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
    private static final int STATUS_LIGHT_GAP = 11;
    private static final int STATUS_LIGHT_GROUP_H = STATUS_LIGHT_SIZE * 3 + STATUS_LIGHT_GAP * 2;
    private static final int STATUS_LIGHT_X = STATUS_AREA_X + EDGE;
    private static final int STATUS_LIGHT_START_Y = STATUS_AREA_Y + 26;
    private static final int STATUS_ROW_0_Y = STATUS_LIGHT_START_Y + 4;
    private static final int STATUS_ROW_1_Y = STATUS_ROW_0_Y + STATUS_LIGHT_SIZE + STATUS_LIGHT_GAP;
    private static final int STATUS_ROW_2_Y = STATUS_ROW_1_Y + STATUS_LIGHT_SIZE + STATUS_LIGHT_GAP;
    private static final int STATS_AREA_X = STATUS_AREA_X + STATUS_AREA_W + GAP;
    private static final int STATS_AREA_Y = TOP_AREA_Y;
    private static final int STATS_AREA_W = 132;
    private static final int STATS_AREA_H = TOP_AREA_H;
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
            NEHostSnapshots.readTasks(buf)
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

    private void drawStatsArea(GUIContext context) {
        drawScaledFittedText(context, tr("gui.neoecoae.crafting.stats", "Stats"), STATS_AREA_X + EDGE, STATS_AREA_Y + EDGE,
                TEXT_SCALE, STATS_AREA_W - EDGE * 2, TEXT_PRIMARY);
        int x = STATS_AREA_X + EDGE;
        int y = STATS_AREA_Y + 23;
        drawScaledPairLine(context, trString("gui.neoecoae.crafting.recipe_slots", "Tasks") + ": ",
                snapshot.runningThreadCount(), snapshot.threadCount(), x, y);
        drawProgressBar(context, x, y + 12, STATS_AREA_W - EDGE * 2, 9, snapshot.runningThreadCount(), snapshot.threadCount(), TEXT_SUCCESS);
        y += 31;
        drawScaledValueLine(context, trString("gui.neoecoae.crafting.batch_parallel", "Free") + ": ",
                snapshot.availableThreads(), x, y, STATS_AREA_W - EDGE * 2);
        y += 16;
        drawScaledValueLine(context, trString("gui.neoecoae.crafting.patterns_short", "Patterns") + ": ",
                snapshot.patternBusCount(), x, y, 60);
        drawScaledValueLine(context, trString("gui.neoecoae.crafting.ft_cores_short", "FT Cores") + ": ",
                snapshot.parallelCount(), x + 66, y, STATS_AREA_W - EDGE * 2 - 66);
    }

    private void drawScaledValueLine(GUIContext context, String label, long value, int x, int y, int maxWidth) {
        String valueText = fullNumber(value);
        int valueWidth = Math.round(context.mc.font.width(valueText) * TEXT_SCALE);
        drawScaledFittedText(context, label, x, y, TEXT_SCALE, Math.max(0, maxWidth - valueWidth), TEXT_MUTED);
        drawScaledText(context, valueText, x + maxWidth - valueWidth, y, TEXT_SCALE, TEXT_VALUE);
    }

    private void drawScaledPairLine(GUIContext context, String label, long used, long total, int x, int y) {
        float cursor = x;
        drawScaledText(context, label, cursor, y, TEXT_SCALE, TEXT_MUTED);
        cursor += context.mc.font.width(label) * TEXT_SCALE;
        String usedText = fullNumber(used);
        String totalText = fullNumber(total);
        drawScaledText(context, usedText, cursor, y, TEXT_SCALE, TEXT_SUCCESS);
        cursor += context.mc.font.width(usedText) * TEXT_SCALE;
        drawScaledText(context, " / ", cursor, y, TEXT_SCALE, TEXT_MUTED);
        cursor += context.mc.font.width(" / ") * TEXT_SCALE;
        drawScaledText(context, totalText, cursor, y, TEXT_SCALE, TEXT_VALUE);
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
        taskCards.onMouseWheel(this, event, snapshot.tasks().size(), TASK_LAYOUT);
    }

    private HoverTooltips tooltipAt(double mouseX, double mouseY) {
        HoverTooltips status = statusTooltip(mouseX, mouseY);
        if (status != null) {
            return status;
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
        if (!containsLocal(STATS_AREA_X, STATS_AREA_Y, STATS_AREA_W, STATS_AREA_H, mouseX, mouseY)) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.neoecoae.crafting.stats"));
        lines.add(Component.translatable("gui.neoecoae.crafting.parallel_core_count", snapshot.parallelCount()));
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
        List<NECraftingTaskEntry> tasks
    ) {
        private static final CraftingSnapshot EMPTY = new CraftingSnapshot(
            false, false, 0, 0, 0, 0, 0, 0, false, false, 0, 0L, List.of()
        );
    }
}
