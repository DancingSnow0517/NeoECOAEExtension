package cn.dancingsnow.neoecoae.gui.host;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class NEComputationAeCanvas extends NEHostCanvas {
    static final int UI_WIDTH = 344;
    static final int UI_HEIGHT = 252;
    private static final int PANEL_MARGIN = 7;
    private static final int MAIN_PANEL_X = PANEL_MARGIN;
    private static final int MAIN_PANEL_Y = 24;
    private static final int MAIN_PANEL_W = PLAYER_INVENTORY_WIDTH + 2;
    private static final int MAIN_PANEL_H = 132;
    static final int TOOLBAR_BUTTON_X = UI_WIDTH - PANEL_MARGIN - 16;
    static final int TOOLBAR_BUTTON_Y = 4;
    static final int TOOLBAR_BUTTON_W = 16;
    static final int TOOLBAR_BUTTON_H = 16;
    private static final int THREAD_BAR_X = MAIN_PANEL_X + 78;
    private static final int THREAD_BAR_Y = MAIN_PANEL_Y + 20;
    private static final int THREAD_BAR_W = MAIN_PANEL_X + MAIN_PANEL_W - THREAD_BAR_X - 12;
    private static final int THREAD_BAR_H = 9;
    private static final int STORAGE_BAR_X = THREAD_BAR_X;
    private static final int STORAGE_BAR_Y = MAIN_PANEL_Y + 67;
    private static final int STORAGE_BAR_W = THREAD_BAR_W;
    private static final int STORAGE_BAR_H = 9;
    static final int PLAYER_INV_X = MAIN_PANEL_X + 1;
    private static final int PLAYER_INV_LABEL_Y = 159;
    static final int PLAYER_INV_Y = 171;
    static final int PLAYER_HOTBAR_Y = 229;
    private static final int TASK_PANEL_GAP = 8;
    private static final int TASK_PANEL_X = MAIN_PANEL_X + MAIN_PANEL_W + TASK_PANEL_GAP;
    private static final int TASK_PANEL_Y = MAIN_PANEL_Y;
    private static final int TASK_PANEL_W = UI_WIDTH - TASK_PANEL_X - PANEL_MARGIN;
    private static final int TASK_PANEL_H = PLAYER_HOTBAR_Y + 18 - TASK_PANEL_Y;
    private static final int TASK_CARD_X = TASK_PANEL_X + 8;
    private static final int TASK_CARD_Y = TASK_PANEL_Y + 19;
    private static final int TASK_CARD_W = TASK_PANEL_W - 16;
    private static final int TASK_CARD_H = 18;
    private static final int TASK_CARD_STRIDE = 20;
    private static final int TASK_LIST_BOTTOM_Y = TASK_PANEL_Y + TASK_PANEL_H - 3;
    private static final float COMPACT_TEXT_SCALE = 0.85F;
    private static final NEAnimatedTaskCards.Layout TASK_LAYOUT = new NEAnimatedTaskCards.Layout(
            TASK_PANEL_X, TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H,
            TASK_CARD_X, TASK_CARD_Y, TASK_CARD_W, TASK_CARD_H, TASK_CARD_STRIDE, TASK_LIST_BOTTOM_Y,
            TASK_PANEL_X + 4, TASK_PANEL_W - 8,
            TASK_PANEL_X + TASK_PANEL_W - 5, 3, TASK_LIST_BOTTOM_Y - TASK_CARD_Y
    );
    private static final NEAnimatedTaskCards.CardStyle TASK_CARD_STYLE = NEAnimatedTaskCards.CardStyle.computation();

    private final ECOComputationSystemBlockEntity computation;
    private final Consumer<CpuSelectionMode> cpuModeConsumer;
    private final NEAnimatedTaskCards taskCards = new NEAnimatedTaskCards();
    private ComputationSnapshot snapshot = ComputationSnapshot.EMPTY;

    NEComputationAeCanvas(ECOComputationSystemBlockEntity computation, Consumer<CpuSelectionMode> cpuModeConsumer) {
        super(UI_WIDTH, UI_HEIGHT);
        this.computation = computation;
        this.cpuModeConsumer = cpuModeConsumer;
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
            buf.writeBoolean(computation.isFormed());
            buf.writeBoolean(computation.isHostActive());
            buf.writeVarInt(Math.max(0, computation.getUsedThread()));
            buf.writeVarInt(Math.max(0, computation.getTotalThread()));
            buf.writeVarInt(Math.max(0, computation.getParallelCount()));
            buf.writeVarInt(Math.max(0, computation.getParallelCoreCount()));
            buf.writeVarLong(Math.max(0L, computation.getUsedComputationBytes()));
            buf.writeVarLong(Math.max(0L, computation.getTotalBytes()));
            buf.writeVarInt(computation.getCpuSelectionMode().ordinal());
            NEHostSnapshots.writeTasks(buf, computation.createComputationTasks());
        });
    }

    @Override
    protected void acceptSnapshot(byte[] snapshotData) {
        NEHostSnapshots.decode(snapshotData, buf -> {
            CpuSelectionMode[] modes = CpuSelectionMode.values();
            boolean formed = buf.readBoolean();
            boolean active = buf.readBoolean();
            int usedThreads = Math.max(0, buf.readVarInt());
            int totalThreads = Math.max(0, buf.readVarInt());
            int parallelCount = Math.max(0, buf.readVarInt());
            int parallelCores = Math.max(0, buf.readVarInt());
            long usedComputationBytes = Math.max(0L, buf.readVarLong());
            long totalBytes = Math.max(0L, buf.readVarLong());
            CpuSelectionMode cpuSelectionMode = modes[Math.clamp(buf.readVarInt(), 0, modes.length - 1)];
            this.snapshot = new ComputationSnapshot(
                formed,
                active,
                usedThreads,
                totalThreads,
                parallelCount,
                parallelCores,
                usedComputationBytes,
                totalBytes,
                cpuSelectionMode,
                NEHostSnapshots.readTasks(buf)
            );
            cpuModeConsumer.accept(cpuSelectionMode);
        });
    }

    @Override
    protected void drawHostBackground(GUIContext context) {
        drawInsetRect(context, MAIN_PANEL_X, MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);
        drawInsetRect(context, TASK_PANEL_X, TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H);
        drawHeader(context);
        drawMainStats(context);
        drawInventory(context);
        drawTasks(context);
    }

    private void drawHeader(GUIContext context) {
        Component formedLabel = tr("gui.neoecoae.machine.formed", "Formed").append(": ");
        Component formedValue = boolText(snapshot.formed());
        Component activeLabel = Component.literal("    ")
                .append(tr("gui.neoecoae.machine.active", "Active"))
                .append(": ");
        Component activeValue = boolText(snapshot.active());
        int width = Math.round((context.mc.font.width(formedLabel) + context.mc.font.width(formedValue)
                + context.mc.font.width(activeLabel) + context.mc.font.width(activeValue)) * COMPACT_TEXT_SCALE);
        float x = Math.max(8, TOOLBAR_BUTTON_X - 4 - width);
        drawFittedText(context, computation.getHostTitle(), 8, 8, Math.max(40, Math.round(x) - 12), TEXT_TITLE);
        drawScaledText(context, formedLabel, x, 8, COMPACT_TEXT_SCALE, 0xFF4A4A4A);
        x += context.mc.font.width(formedLabel) * COMPACT_TEXT_SCALE;
        drawScaledText(context, formedValue, x, 8, COMPACT_TEXT_SCALE, snapshot.formed() ? TEXT_SUCCESS : TEXT_ERROR);
        x += context.mc.font.width(formedValue) * COMPACT_TEXT_SCALE;
        drawScaledText(context, activeLabel, x, 8, COMPACT_TEXT_SCALE, 0xFF4A4A4A);
        x += context.mc.font.width(activeLabel) * COMPACT_TEXT_SCALE;
        drawScaledText(context, activeValue, x, 8, COMPACT_TEXT_SCALE, snapshot.active() ? TEXT_SUCCESS : TEXT_MUTED);
    }

    private void drawMainStats(GUIContext context) {
        int x = MAIN_PANEL_X + 8;
        int y = MAIN_PANEL_Y + 8;
        drawPairLine(context, trString("gui.neoecoae.computation.threads", "Threads") + ": ",
                snapshot.usedThreads(), snapshot.totalThreads(), x, y);
        drawProgressBar(context, THREAD_BAR_X, THREAD_BAR_Y, THREAD_BAR_W, THREAD_BAR_H,
                snapshot.usedThreads(), snapshot.totalThreads(), TEXT_SUCCESS);
        y += 12;
        drawText(context, tr("gui.neoecoae.computation.parallel_count", "Parallel Capacity: %s", NEHostFormat.number(snapshot.parallelCount())),
                x, y, TEXT_PRIMARY);
        y += 12;
        drawModeLine(context, x, y);
        y += 24;
        drawPairTextLine(context, trString("gui.neoecoae.computation.storage_used", "Storage") + ": ",
                NEHostFormat.bytes(snapshot.usedComputationBytes()), NEHostFormat.bytes(snapshot.totalBytes()), x, y);
        drawProgressBar(context, STORAGE_BAR_X, STORAGE_BAR_Y, STORAGE_BAR_W, STORAGE_BAR_H,
                snapshot.usedComputationBytes(), snapshot.totalBytes(), TEXT_BLUE);
        y += 12;
        drawText(context, tr("gui.neoecoae.computation.parallel_cores", "Parallel Cores: %s", NEHostFormat.number(snapshot.parallelCores())),
                x, y, TEXT_PRIMARY);
    }

    private void drawModeLine(GUIContext context, int x, int y) {
        String label = trString("gui.neoecoae.computation.cpu_selection_mode.short", "Mode") + ": ";
        drawText(context, label, x, y, TEXT_MUTED);
        drawFittedText(context, cpuModeShortLabel(snapshot.cpuSelectionMode()), x + context.mc.font.width(label), y,
                MAIN_PANEL_W - 16 - context.mc.font.width(label), TEXT_VALUE);
    }

    private void drawPairLine(GUIContext context, String label, long used, long total, int x, int y) {
        drawPairTextLine(context, label, NEHostFormat.number(used), NEHostFormat.number(total), x, y);
    }

    private void drawPairTextLine(GUIContext context, String label, String used, String total, int x, int y) {
        int cursor = context.mc.font.width(label);
        drawText(context, label, x, y, TEXT_MUTED);
        drawText(context, used, x + cursor, y, TEXT_SUCCESS);
        cursor += context.mc.font.width(used);
        drawText(context, " / ", x + cursor, y, TEXT_MUTED);
        cursor += context.mc.font.width(" / ");
        drawText(context, total, x + cursor, y, TEXT_VALUE);
    }

    private void drawInventory(GUIContext context) {
        drawFittedText(context, tr("gui.neoecoae.common.inventory", "Inventory"),
                PLAYER_INV_X, PLAYER_INV_LABEL_Y, PLAYER_INVENTORY_WIDTH, TEXT_MUTED);
    }

    private void drawTasks(GUIContext context) {
        List<NECraftingTaskEntry> entries = snapshot.tasks();
        drawFittedText(context, tr("gui.neoecoae.crafting.tasks", "Crafting Tasks"), TASK_PANEL_X + 8, TASK_PANEL_Y + 6, TASK_PANEL_W - 32, TEXT_PRIMARY);
        drawRightText(context, NEHostFormat.number(entries.size()), TASK_PANEL_X + TASK_PANEL_W - 8, TASK_PANEL_Y + 6, TEXT_VALUE);
        if (entries.isEmpty()) {
            taskCards.resetIfEmpty(entries);
            drawCenteredText(context, tr("gui.neoecoae.crafting.no_tasks", "No tasks"),
                    TASK_PANEL_X, TASK_PANEL_Y + TASK_PANEL_H / 2.0F - 4, TASK_PANEL_W, TEXT_MUTED);
            return;
        }
        taskCards.draw(this, context, entries, TASK_LAYOUT, TASK_CARD_STYLE);
    }

    private void onMouseWheel(UIEvent event) {
        taskCards.onMouseWheel(this, event, snapshot.tasks().size(), TASK_LAYOUT);
    }

    private HoverTooltips tooltipAt(double mouseX, double mouseY) {
        if (containsLocal(THREAD_BAR_X, THREAD_BAR_Y, THREAD_BAR_W, THREAD_BAR_H, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                    tr("gui.neoecoae.computation.threads", "Threads"),
                    Component.literal(NEHostFormat.usedTotal(snapshot.usedThreads(), snapshot.totalThreads())).withStyle(ChatFormatting.WHITE)
            ), null, null, null);
        }
        if (containsLocal(STORAGE_BAR_X, STORAGE_BAR_Y, STORAGE_BAR_W, STORAGE_BAR_H, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                    tr("gui.neoecoae.computation.available_storage", "Available Storage"),
                    Component.literal(NEHostFormat.usedTotalBytes(snapshot.usedComputationBytes(), snapshot.totalBytes())).withStyle(ChatFormatting.WHITE)
            ), null, null, null);
        }
        return taskTooltip(mouseX, mouseY);
    }

    private HoverTooltips taskTooltip(double mouseX, double mouseY) {
        return taskCards.tooltipAt(this, snapshot.tasks(), TASK_LAYOUT, mouseX, mouseY);
    }

    private static Component cpuModeShortLabel(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> tr("gui.neoecoae.computation.cpu_selection_mode.short.player", "Player");
            case MACHINE_ONLY -> tr("gui.neoecoae.computation.cpu_selection_mode.short.machine", "Machine");
            case ANY -> tr("gui.neoecoae.computation.cpu_selection_mode.short.any", "Any");
        };
    }

    private record ComputationSnapshot(
        boolean formed,
        boolean active,
        int usedThreads,
        int totalThreads,
        int parallelCount,
        int parallelCores,
        long usedComputationBytes,
        long totalBytes,
        CpuSelectionMode cpuSelectionMode,
        List<NECraftingTaskEntry> tasks
    ) {
        private static final ComputationSnapshot EMPTY = new ComputationSnapshot(
            false, false, 0, 0, 0, 0, 0L, 0L, CpuSelectionMode.ANY, List.of()
        );
    }
}
