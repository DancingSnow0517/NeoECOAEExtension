package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NEAnimatedTaskCards {
    private static final long TASK_FADE_MS = 360L;
    private static final long TASK_MOVE_MS = 140L;

    private final Map<String, TaskCardAnimation> animations = new LinkedHashMap<>();
    private List<Frame> lastFrames = List.of();
    private int lastScrollOffset;
    private int scrollOffset;
    private boolean initialized;

    int draw(
        NEHostCanvas canvas,
        GUIContext context,
        List<NECraftingTaskEntry> entries,
        Layout layout,
        CardStyle style
    ) {
        int visibleRows = layout.visibleRows();
        int currentScrollOffset = scrollOffset(entries.size(), visibleRows);
        context.graphics.flush();
        context.enableScissor(canvas.absX(layout.scissorX()), canvas.absY(layout.cardY()), layout.scissorW(), layout.scissorHeight());
        for (Frame frame : update(entries, visibleRows, layout.cardY(), layout.rowStride())) {
            drawTaskCard(canvas, context, frame, layout, style);
        }
        context.graphics.flush();
        context.disableScissor();
        canvas.drawListScrollbar(context, entries.size(), visibleRows, currentScrollOffset,
                layout.scrollbarX(), layout.cardY(), layout.scrollbarW(), layout.scrollbarHeight());
        return currentScrollOffset;
    }

    private List<Frame> update(
        List<NECraftingTaskEntry> entries,
        int visibleRows,
        int firstY,
        int rowStride
    ) {
        scrollOffset = clampScroll(scrollOffset, entries.size(), visibleRows);
        long now = Util.getMillis();
        Set<String> visibleKeys = new HashSet<>();
        int visible = Math.min(visibleRows, Math.max(0, entries.size() - scrollOffset));
        int entryOffset = initialized ? Mth.clamp((scrollOffset - lastScrollOffset) * rowStride, -rowStride, rowStride) : 0;
        for (int i = 0; i < visible; i++) {
            int entryIndex = scrollOffset + i;
            NECraftingTaskEntry entry = entries.get(entryIndex);
            String key = taskEntryKey(entry, entryIndex);
            int targetY = firstY + i * rowStride;
            visibleKeys.add(key);
            TaskCardAnimation animation = animations.get(key);
            if (animation == null) {
                animation = new TaskCardAnimation(entry, targetY, entryOffset);
                animations.put(key, animation);
            } else {
                animation.entry = entry;
                animation.targetY = targetY;
                animation.exiting = false;
            }
        }

        for (Map.Entry<String, TaskCardAnimation> entry : animations.entrySet()) {
            TaskCardAnimation animation = entry.getValue();
            if (!visibleKeys.contains(entry.getKey()) && !animation.exiting) {
                animation.exiting = true;
                animation.exitStartedMs = now;
            }
        }

        List<Frame> frames = new ArrayList<>();
        animations.entrySet().removeIf(entry -> {
            TaskCardAnimation animation = entry.getValue();
            animation.update(now);
            if (animation.exiting && animation.alpha <= 0.02F) {
                return true;
            }
            frames.add(new Frame(animation.entry, animation.y, animation.alpha, animation.exiting));
            return false;
        });
        frames.sort(Comparator.comparingDouble(Frame::y));
        lastFrames = List.copyOf(frames);
        lastScrollOffset = scrollOffset;
        initialized = true;
        return frames;
    }

    boolean onMouseWheel(
        NEHostCanvas canvas,
        UIEvent event,
        int total,
        Layout layout
    ) {
        return onMouseWheel(canvas, event, total, layout.visibleRows(),
                layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH());
    }

    boolean onMouseWheel(
        NEHostCanvas canvas,
        UIEvent event,
        int total,
        int visibleRows,
        int panelX,
        int panelY,
        int panelW,
        int panelH
    ) {
        scrollOffset = clampScroll(scrollOffset, total, visibleRows);
        if (!canvas.containsLocal(panelX, panelY, panelW, panelH, canvas.currentMouseX(), canvas.currentMouseY())
                || total <= visibleRows) {
            return false;
        }
        scrollOffset = clampScroll(scrollOffset + (event.deltaY < 0 ? 1 : -1), total, visibleRows);
        event.stopPropagation();
        return true;
    }

    HoverTooltips tooltipAt(
        NEHostCanvas canvas,
        List<NECraftingTaskEntry> entries,
        Layout layout,
        double mouseX,
        double mouseY
    ) {
        if (entries.isEmpty()) {
            return null;
        }
        scrollOffset = clampScroll(scrollOffset, entries.size(), layout.visibleRows());
        NECraftingTaskEntry entry = hitEntry(canvas, entries, layout, mouseX, mouseY);
        return entry == null ? null : taskTooltip(entry);
    }

    int scrollOffset(int total, int visibleRows) {
        scrollOffset = clampScroll(scrollOffset, total, visibleRows);
        return scrollOffset;
    }

    void resetIfEmpty(List<NECraftingTaskEntry> entries) {
        if (!entries.isEmpty()) {
            return;
        }
        animations.clear();
        lastFrames = List.of();
        scrollOffset = 0;
        lastScrollOffset = 0;
        initialized = false;
    }

    static int visibleRows(int firstY, int bottomY, int rowHeight, int rowStride) {
        return NEHostCanvas.visibleRows(firstY, bottomY, rowHeight, rowStride);
    }

    private NECraftingTaskEntry hitEntry(
        NEHostCanvas canvas,
        List<NECraftingTaskEntry> entries,
        Layout layout,
        double mouseX,
        double mouseY
    ) {
        if (!lastFrames.isEmpty()) {
            for (Frame frame : lastFrames) {
                if (frame.exiting() || frame.alpha() < 0.35F) {
                    continue;
                }
                float hitY = Math.max(frame.y(), layout.cardY());
                float hitBottom = Math.min(frame.y() + layout.cardH(), layout.listBottomY());
                float hitH = hitBottom - hitY;
                if (hitH <= 0.0F) {
                    continue;
                }
                if (canvas.containsLocal(layout.cardX(), hitY, layout.cardW(), hitH, mouseX, mouseY)) {
                    return frame.entry();
                }
            }
            return null;
        }
        int visibleRows = layout.visibleRows();
        int visible = Math.min(visibleRows, Math.max(0, entries.size() - scrollOffset));
        for (int i = 0; i < visible; i++) {
            int y = layout.cardY() + i * layout.rowStride();
            if (canvas.containsLocal(layout.cardX(), y, layout.cardW(), layout.cardH(), mouseX, mouseY)) {
                return entries.get(scrollOffset + i);
            }
        }
        return null;
    }

    private static void drawTaskCard(
        NEHostCanvas canvas,
        GUIContext context,
        Frame frame,
        Layout layout,
        CardStyle style
    ) {
        float y = frame.y();
        float alpha = frame.alpha();
        if (y + layout.cardH() <= layout.cardY() || y >= layout.listBottomY() || alpha <= 0.02F) {
            return;
        }
        NECraftingTaskEntry entry = frame.entry();
        int color = entry.statusColor();
        canvas.drawTaskCardFrame(context, layout.cardX(), y, layout.cardW(), layout.cardH(), alpha);
        float contentX = layout.cardX() + style.contentInsetX();
        String amount = "x" + NEHostFormat.number(entry.outputAmount());
        int amountW = Math.round(context.mc.font.width(amount) * style.textScale());
        float amountX = layout.cardX() + layout.cardW() - style.amountRightInset() - amountW;
        int progressW = layout.cardW() - style.contentInsetX() - style.progressRightInset();
        if (style.progressReservesAmount()) {
            progressW -= amountW;
        }
        progressW = Math.max(12, progressW);
        float progressY = y + layout.cardH() - style.progressBottomInset();
        canvas.fillLocal(context, contentX, progressY, progressW, style.progressHeight(), canvas.withAlpha(0xAA17141E, alpha));
        int fill = entry.progressWidth(progressW);
        if (fill > 0) {
            canvas.fillLocal(context, contentX, progressY, fill, style.progressHeight(), canvas.withAlpha(color, alpha));
        }
        canvas.fillLocal(context, layout.cardX() + 3, y + layout.cardH() - style.statusLineBottomInset(),
                layout.cardW() - 6, style.statusLineHeight(), canvas.withAlpha(color, alpha));

        boolean stableContent = !style.stableContentOnly()
                || (!frame.exiting() && alpha > 0.90F && y >= layout.cardY() && y + layout.cardH() <= layout.listBottomY());
        if (!stableContent) {
            return;
        }
        canvas.drawScaledItem(context, entry.output(), layout.cardX() + style.itemInsetX(), y + style.itemInsetY(),
                style.itemScale(), alpha);
        int nameWidth = Math.round((amountX - contentX - style.amountGap()) / Math.max(0.01F, style.textScale()));
        String name = canvas.fit(context, entry.output().getHoverName().getString(), Math.max(16, nameWidth));
        canvas.drawScaledText(context, name, contentX, y + style.textOffsetY(), style.textScale(), canvas.withAlpha(NEHostCanvas.TEXT_PRIMARY, alpha));
        canvas.drawScaledText(context, amount, amountX, y + style.textOffsetY(), style.textScale(), canvas.withAlpha(NEHostCanvas.TEXT_VALUE, alpha));
    }

    private static HoverTooltips taskTooltip(NECraftingTaskEntry entry) {
        List<Component> lines = NEHostCanvas.itemTooltip(entry.output());
        lines.add(Component.translatable(entry.statusKey()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.neoecoae.crafting.task.amount", NEHostFormat.number(entry.outputAmount())));
        lines.add(Component.translatable("gui.neoecoae.crafting.task.crafts", NEHostFormat.number(entry.craftCount())));
        if (entry.totalTicks() > 0L) {
            lines.add(Component.translatable("gui.neoecoae.crafting.task.time",
                    entry.elapsedTimeText(), entry.totalTimeText()).withStyle(ChatFormatting.AQUA));
        }
        return new HoverTooltips(lines, entry.output().getTooltipImage().orElse(null), null, entry.output());
    }

    private static int clampScroll(int value, int total, int visibleRows) {
        return NEHostCanvas.clampScroll(value, total, visibleRows);
    }

    private static String taskEntryKey(NECraftingTaskEntry entry, int index) {
        return entry.id() == null || entry.id().isBlank() ? "task:" + index : entry.id();
    }

    record Frame(NECraftingTaskEntry entry, float y, float alpha, boolean exiting) {
    }

    record Layout(
        int panelX,
        int panelY,
        int panelW,
        int panelH,
        int cardX,
        int cardY,
        int cardW,
        int cardH,
        int rowStride,
        int listBottomY,
        int scissorX,
        int scissorW,
        int scrollbarX,
        int scrollbarW,
        float scrollbarHeight
    ) {
        int visibleRows() {
            return NEAnimatedTaskCards.visibleRows(cardY, listBottomY, cardH, rowStride);
        }

        int scissorHeight() {
            return Math.max(0, listBottomY - cardY + 1);
        }
    }

    record CardStyle(
        float textScale,
        float itemScale,
        int itemInsetX,
        int itemInsetY,
        int contentInsetX,
        int textOffsetY,
        int amountRightInset,
        int amountGap,
        int progressBottomInset,
        int progressHeight,
        int progressRightInset,
        boolean progressReservesAmount,
        int statusLineBottomInset,
        int statusLineHeight,
        boolean stableContentOnly
    ) {
        static CardStyle crafting(float textScale, float itemScale) {
            return new CardStyle(textScale, itemScale, 2, 1, 19, 3, 5, 6, 3, 2, 10, true, 2, 1, true);
        }

        static CardStyle computation() {
            return new CardStyle(1.0F, 1.0F, 1, 1, 21, 5, 5, 5, 4, 2, 5, false, 3, 1, false);
        }
    }

    private static final class TaskCardAnimation {
        private NECraftingTaskEntry entry;
        private float y;
        private int targetY;
        private float alpha;
        private long lastUpdateMs;
        private boolean exiting;
        private long exitStartedMs;

        private TaskCardAnimation(NECraftingTaskEntry entry, int targetY, int entryOffset) {
            this.entry = entry;
            this.targetY = targetY;
            this.y = targetY + entryOffset;
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
                float fadeStep = TASK_FADE_MS <= 0L ? 1.0F : Mth.clamp((float) elapsed / (float) TASK_FADE_MS, 0.0F, 1.0F);
                alpha = Math.min(1.0F, alpha + fadeStep);
            }
        }
    }
}
