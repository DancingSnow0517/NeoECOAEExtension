package cn.dancingsnow.neoecoae.gui.host;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

final class NEComputationLegacyCanvas extends NEHostCanvas {
    static final int UI_WIDTH = 344;
    static final int UI_HEIGHT = 252;
    private static final int PANEL_MARGIN = 7;
    private static final int MAIN_PANEL_X = PANEL_MARGIN;
    private static final int MAIN_PANEL_Y = 24;
    private static final int MAIN_PANEL_W = 18 * 9 + 2;
    private static final int MAIN_PANEL_H = 132;
    static final int TOOLBAR_BUTTON_X = UI_WIDTH - PANEL_MARGIN - 18;
    static final int TOOLBAR_BUTTON_Y = 4;
    static final int TOOLBAR_BUTTON_W = 18;
    static final int TOOLBAR_BUTTON_H = 20;
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

    private final ECOComputationSystemBlockEntity computation;
    private final NEAnimatedTaskCards taskCards = new NEAnimatedTaskCards();
    private int taskScrollOffset;
    private ComputationSnapshot snapshot = ComputationSnapshot.EMPTY;

    NEComputationLegacyCanvas(ECOComputationSystemBlockEntity computation) {
        super(UI_WIDTH, UI_HEIGHT);
        this.computation = computation;
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
            buf.writeBoolean(computation.isFormed());
            buf.writeBoolean(computation.isHostActive());
            buf.writeVarInt(Math.max(0, computation.getUsedThread()));
            buf.writeVarInt(Math.max(0, computation.getTotalThread()));
            buf.writeVarInt(Math.max(0, computation.getParallelCount()));
            buf.writeVarInt(Math.max(0, computation.getAcceleratorCount()));
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
            this.snapshot = new ComputationSnapshot(
                buf.readBoolean(),
                buf.readBoolean(),
                Math.max(0, buf.readVarInt()),
                Math.max(0, buf.readVarInt()),
                Math.max(0, buf.readVarInt()),
                Math.max(0, buf.readVarInt()),
                Math.max(0L, buf.readVarLong()),
                Math.max(0L, buf.readVarLong()),
                modes[Math.clamp(buf.readVarInt(), 0, modes.length - 1)],
                NEHostSnapshots.readTasks(buf)
            );
        });
    }

    @Override
    protected void drawHostBackground(GUIContext context) {
        drawInsetRect(context, MAIN_PANEL_X, MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);
        drawInsetRect(context, TASK_PANEL_X, TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H);
        drawToolbarButton(context, TOOLBAR_BUTTON_X, TOOLBAR_BUTTON_Y, TOOLBAR_BUTTON_W, TOOLBAR_BUTTON_H,
                containsLocal(TOOLBAR_BUTTON_X, TOOLBAR_BUTTON_Y, TOOLBAR_BUTTON_W, TOOLBAR_BUTTON_H, context.mouseX, context.mouseY));
        drawCpuModeIcon(context);
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
        drawFittedText(context, computation.getHostTitle(), 8, 8, Math.max(40, Math.round(x) - 12), TEXT_PRIMARY);
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
        drawFittedText(context, tr("gui.neoecoae.computation.parallel_count", "Parallel: %s", NEHostFormat.number(snapshot.parallelCount())),
                x, y, MAIN_PANEL_W - 16, TEXT_PRIMARY);
        y += 12;
        drawModeLine(context, x, y);
        y += 24;
        drawPairTextLine(context, trString("gui.neoecoae.computation.storage_used", "Storage") + ": ",
                NEHostFormat.bytes(snapshot.usedComputationBytes()), NEHostFormat.bytes(snapshot.totalBytes()), x, y);
        drawProgressBar(context, STORAGE_BAR_X, STORAGE_BAR_Y, STORAGE_BAR_W, STORAGE_BAR_H,
                snapshot.usedComputationBytes(), snapshot.totalBytes(), TEXT_BLUE);
        y += 12;
        drawFittedText(context, tr("gui.neoecoae.computation.accelerators", "Accelerators: %s", NEHostFormat.number(snapshot.accelerators())),
                x, y, MAIN_PANEL_W - 16, TEXT_PRIMARY);
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

    private void drawCpuModeIcon(GUIContext context) {
        NEAeSprite icon = switch (snapshot.cpuSelectionMode()) {
            case PLAYER_ONLY -> NEAeSprite.CRAFT_HAMMER;
            case MACHINE_ONLY -> NEAeSprite.BACKGROUND_WIRELESS_TERM;
            case ANY -> NEAeSprite.TYPE_FILTER_ALL;
        };
        drawIcon(context, icon,
                TOOLBAR_BUTTON_X + (TOOLBAR_BUTTON_W - icon.width()) / 2.0F,
                TOOLBAR_BUTTON_Y + (TOOLBAR_BUTTON_H - icon.height()) / 2.0F);
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
        drawFittedText(context, tr("gui.neoecoae.crafting.tasks", "Crafting Tasks"), TASK_PANEL_X + 8, TASK_PANEL_Y + 6, TASK_PANEL_W - 32, TEXT_PRIMARY);
        drawRightText(context, NEHostFormat.number(entries.size()), TASK_PANEL_X + TASK_PANEL_W - 8, TASK_PANEL_Y + 6, TEXT_VALUE);
        if (entries.isEmpty()) {
            drawCenteredText(context, tr("gui.neoecoae.crafting.no_tasks", "No tasks"),
                    TASK_PANEL_X, TASK_PANEL_Y + TASK_PANEL_H / 2.0F - 4, TASK_PANEL_W, TEXT_MUTED);
            return;
        }
        context.graphics.flush();
        context.enableScissor(absX(TASK_PANEL_X + 4), absY(TASK_CARD_Y), TASK_PANEL_W - 8, TASK_LIST_BOTTOM_Y - TASK_CARD_Y + 1);
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
        drawItem(context, entry.output(), TASK_CARD_X + 1, y + 1);
        String amount = "x" + NEHostFormat.number(entry.outputAmount());
        int amountW = context.mc.font.width(amount);
        String name = NEHostDraw.fit(context, entry.output().getHoverName().getString(), Math.max(16, TASK_CARD_W - 31 - amountW));
        drawText(context, name, TASK_CARD_X + 21, y + 5, withAlpha(TEXT_PRIMARY, alpha));
        drawRightText(context, amount, TASK_CARD_X + TASK_CARD_W - 5, y + 5, withAlpha(TEXT_VALUE, alpha));
        long done = Math.max(0L, entry.totalTicks() - entry.remainingTicks());
        int fill = entry.status() == NECraftingTaskEntry.Status.WAITING_OUTPUT
                ? TASK_CARD_W - 26
                : ratioWidth(done, entry.totalTicks(), TASK_CARD_W - 26);
        if (entry.status() == NECraftingTaskEntry.Status.QUEUED) {
            fill = 1;
        }
        fillLocal(context, TASK_CARD_X + 21, y + TASK_CARD_H - 4, TASK_CARD_W - 26, 2, withAlpha(0xAA17141E, alpha));
        if (fill > 0) {
            fillLocal(context, TASK_CARD_X + 21, y + TASK_CARD_H - 4, fill, 2, withAlpha(color, alpha));
        }
    }

    private void drawTaskScrollbar(GUIContext context, int total) {
        int visible = visibleRows();
        if (total <= visible) {
            return;
        }
        float height = TASK_LIST_BOTTOM_Y - TASK_CARD_Y;
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
        if (containsLocal(TOOLBAR_BUTTON_X, TOOLBAR_BUTTON_Y, TOOLBAR_BUTTON_W, TOOLBAR_BUTTON_H, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                    Component.translatable("gui.neoecoae.computation.cpu_selection_mode"),
                    cpuModeTooltip(snapshot.cpuSelectionMode()),
                    tr("gui.neoecoae.computation.cpu_selection_mode.click", "Click to switch")
            ), null, null, null);
        }
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

    private static Component cpuModeShortLabel(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> tr("gui.neoecoae.computation.cpu_selection_mode.short.player", "Player");
            case MACHINE_ONLY -> tr("gui.neoecoae.computation.cpu_selection_mode.short.machine", "Machine");
            case ANY -> tr("gui.neoecoae.computation.cpu_selection_mode.short.any", "Any");
        };
    }

    private static Component cpuModeTooltip(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> tr("gui.neoecoae.computation.cpu_selection_mode.player_only", "Only accepts crafting requests from players.");
            case MACHINE_ONLY -> tr("gui.neoecoae.computation.cpu_selection_mode.machine_only", "Only accepts crafting requests from machines.");
            case ANY -> tr("gui.neoecoae.computation.cpu_selection_mode.any", "Accepts all crafting requests.");
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

    private record ComputationSnapshot(
        boolean formed,
        boolean active,
        int usedThreads,
        int totalThreads,
        int parallelCount,
        int accelerators,
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
