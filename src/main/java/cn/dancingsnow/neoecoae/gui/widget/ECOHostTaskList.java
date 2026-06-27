package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.host.NECraftingTaskEntry;
import cn.dancingsnow.neoecoae.gui.host.NEHostFormat;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The crafting-tasks panel: a header with a live count plus a vertically-scrolling list of
 * task cards. The task entries (item + progress, a dynamic list) are synced from the server as an
 * opaque {@code byte[]} snapshot through LDLib2's binding system, then decoded on the client.
 */
public class ECOHostTaskList extends UIElement {
    private static final int TEXT_PRIMARY = 0xFFD6D0E0;
    private static final int TEXT_VALUE = 0xFF8377FF;
    private static final int TEXT_MUTED = 0xFFAAA4B2;
    private static final int FRAME_EDGE = 0xFFD8D3E4;
    private static final int FRAME_LAYER_1 = 0xFF121016;
    private static final int FRAME_LAYER_2 = 0xFF4D4855;
    private static final int FRAME_FACE = 0xFF2C2735;
    private static final int TRACK = 0xAA17141E;
    private static final long FADE_MS = 360L;
    private static final long MOVE_MS = 140L;
    private static final int ROW_H = 18;
    private static final int ROW_GAP = 1;
    private static final int ITEM_X = 2;
    private static final int ITEM_Y = 1;
    private static final int CONTENT_X = 19;
    private static final int TEXT_Y = 3;
    private static final int AMOUNT_RIGHT = 5;
    private static final int TEXT_GAP = 6;
    private static final int PROGRESS_RIGHT = 10;
    private static final int PROGRESS_BOTTOM = 3;
    private static final int PROGRESS_HEIGHT = 2;
    private static final int STATUS_INSET_X = 3;
    private static final int STATUS_BOTTOM = 2;
    private static final int STATUS_HEIGHT = 1;

    private final Function<byte[], List<NECraftingTaskEntry>> decoder;
    private final Supplier<byte[]> serverSnapshot;
    private final Label countLabel = new Label();
    private final Map<String, TaskCardAnimation> animations = new LinkedHashMap<>();
    private List<Frame> lastFrames = List.of();
    private byte[] cachedSnapshot = new byte[0];
    private byte[] snapshot = new byte[0];
    private List<NECraftingTaskEntry> entries = List.of();
    private int scrollOffset;
    private int lastScrollOffset;
    private boolean initialized;
    private float mouseX;
    private float mouseY;

    public ECOHostTaskList(
        Component title,
        Supplier<byte[]> serverSnapshot,
        Function<byte[], List<NECraftingTaskEntry>> decoder
    ) {
        this.decoder = decoder;
        this.serverSnapshot = serverSnapshot;
        addClass("eco-host-task-list");
        layout(layout -> layout.flexDirection(FlexDirection.COLUMN).gapAll(4).paddingAll(7));

        UIElement header = new UIElement().layout(layout -> layout
            .flexDirection(FlexDirection.ROW)
            .justifyContent(AlignContent.SPACE_BETWEEN)
            .alignItems(AlignItems.CENTER)
            .height(10));
        header.addChild(new Label().setValue(title).textStyle(ECOHostStyles::sectionText));
        header.addChild(countLabel.textStyle(ECOHostStyles::compactValueText));
        addChild(header);

        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            HoverTooltips tooltip = tooltipAt(event.x, event.y);
            if (tooltip == null) {
                tooltip = tooltipAt(mouseX, mouseY);
            }
            if (tooltip != null) {
                event.hoverTooltips = tooltip;
                event.stopPropagation();
            }
        });

        var syncValue = DataBindingBuilder.<byte[]>create(this::cachedSnapshot, ignored -> {})
            .syncType(byte[].class)
            .c2sStrategy(SyncStrategy.NONE)
            .build()
            .getSyncValue();
        syncValue.addListener(this::acceptSnapshot);
        addSyncValue(syncValue);
    }

    private void acceptSnapshot(byte[] data) {
        this.snapshot = data == null ? new byte[0] : data;
        this.entries = decoder.apply(this.snapshot);
    }

    private byte[] cachedSnapshot() {
        byte[] next = serverSnapshot.get();
        if (next == null) {
            next = new byte[0];
        }
        if (!Arrays.equals(cachedSnapshot, next)) {
            cachedSnapshot = next;
        }
        return cachedSnapshot;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        this.mouseX = guiContext.mouseX;
        this.mouseY = guiContext.mouseY;
        drawCards(guiContext);
        super.drawBackgroundAdditional(guiContext);
    }

    private void drawCards(GUIContext context) {
        countLabel.setValue(Component.literal(Integer.toString(entries.size())));
        float listX = getPositionX() + 7.0F;
        float listY = getPositionY() + 21.0F;
        float listW = Math.max(0.0F, getSizeWidth() - 14.0F);
        float listH = Math.max(0.0F, getSizeHeight() - 28.0F);
        if (entries.isEmpty()) {
            scrollOffset = 0;
            lastScrollOffset = 0;
            List<Frame> frames = updateFrames(0, listY, ROW_H + ROW_GAP);
            if (!frames.isEmpty()) {
                context.graphics.flush();
                context.enableScissor(listX, listY, listW, listH);
                for (Frame frame : frames) {
                    drawTaskCard(context, frame, listX, listW, listY, listY + listH);
                }
                context.graphics.flush();
                context.disableScissor();
                return;
            }
            initialized = false;
            drawCenteredText(context, Component.translatable("gui.neoecoae.crafting.no_tasks"), listX, listY + listH / 2.0F - 4.0F, listW, TEXT_MUTED);
            return;
        }
        int visibleRows = visibleRows(listH);
        scrollOffset = clampScroll(scrollOffset, entries.size(), visibleRows);
        List<Frame> frames = updateFrames(visibleRows, listY, ROW_H + ROW_GAP);
        context.graphics.flush();
        context.enableScissor(listX, listY, listW, listH);
        for (Frame frame : frames) {
            drawTaskCard(context, frame, listX, listW, listY, listY + listH);
        }
        context.graphics.flush();
        context.disableScissor();
        drawScrollbar(context, listX + listW - 3, listY, 3, listH, visibleRows);
    }

    private HoverTooltips tooltipAt(float mouseX, float mouseY) {
        if (entries.isEmpty()) {
            return null;
        }
        float listX = getPositionX() + 7.0F;
        float listY = getPositionY() + 21.0F;
        float listW = Math.max(0.0F, getSizeWidth() - 14.0F);
        float listH = Math.max(0.0F, getSizeHeight() - 28.0F);
        if (!UIElement.isMouseOverRect(listX, listY, listW, listH, mouseX, mouseY)) {
            return null;
        }
        for (Frame frame : lastFrames) {
            if (frame.exiting() || frame.alpha() < 0.35F) {
                continue;
            }
            float hitY = Math.max(frame.y(), listY);
            float hitBottom = Math.min(frame.y() + ROW_H, listY + listH);
            float hitH = hitBottom - hitY;
            if (hitH > 0.0F && UIElement.isMouseOverRect(listX, hitY, listW, hitH, mouseX, mouseY)) {
                return taskTooltip(frame.entry());
            }
        }
        return null;
    }

    private void onMouseWheel(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
        float listX = getPositionX() + 7.0F;
        float listY = getPositionY() + 21.0F;
        float listW = Math.max(0.0F, getSizeWidth() - 14.0F);
        float listH = Math.max(0.0F, getSizeHeight() - 28.0F);
        int visibleRows = visibleRows(listH);
        if (entries.size() <= visibleRows || !UIElement.isMouseOverRect(listX, listY, listW, listH, mouseX, mouseY)) {
            return;
        }
        scrollOffset = clampScroll(scrollOffset + (event.deltaY < 0 ? 1 : -1), entries.size(), visibleRows);
        event.stopPropagation();
    }

    private List<Frame> updateFrames(int visibleRows, float firstY, int rowStride) {
        long now = Util.getMillis();
        Set<String> visibleKeys = new HashSet<>();
        int visible = Math.min(visibleRows, Math.max(0, entries.size() - scrollOffset));
        int entryOffset = initialized ? Mth.clamp((scrollOffset - lastScrollOffset) * rowStride, -rowStride, rowStride) : 0;
        for (int i = 0; i < visible; i++) {
            int entryIndex = scrollOffset + i;
            NECraftingTaskEntry entry = entries.get(entryIndex);
            String key = taskEntryKey(entry, entryIndex);
            float targetY = firstY + i * rowStride;
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

    private void drawTaskCard(GUIContext context, Frame frame, float x, float w, float listTop, float listBottom) {
        float y = frame.y();
        float alpha = frame.alpha();
        if (y + ROW_H <= listTop || y >= listBottom || alpha <= 0.02F) {
            return;
        }
        NECraftingTaskEntry entry = frame.entry();
        int color = entry.statusColor();
        fill(context, x, y, w, ROW_H, withAlpha(FRAME_EDGE, alpha));
        fill(context, x + 1, y + 1, w - 2, ROW_H - 2, withAlpha(FRAME_LAYER_1, alpha));
        fill(context, x + 2, y + 2, w - 4, ROW_H - 4, withAlpha(FRAME_LAYER_2, alpha));
        fill(context, x + 3, y + 3, w - 6, ROW_H - 6, withAlpha(FRAME_FACE, alpha));
        fill(context, x + STATUS_INSET_X, y + ROW_H - STATUS_BOTTOM, w - STATUS_INSET_X * 2, STATUS_HEIGHT, withAlpha(color, alpha));

        float contentX = x + CONTENT_X;
        float progressY = y + ROW_H - PROGRESS_BOTTOM;
        Font font = context.mc.font;
        String amount = "x" + NEHostFormat.number(entry.outputAmount());
        int amountW = font.width(amount);
        int progressW = Math.max(12, Math.round(w) - CONTENT_X - PROGRESS_RIGHT - amountW);
        fill(context, contentX, progressY, progressW, PROGRESS_HEIGHT, withAlpha(TRACK, alpha));
        int fillW = entry.progressWidth(progressW);
        if (fillW > 0) {
            fill(context, contentX, progressY, fillW, PROGRESS_HEIGHT, withAlpha(color, alpha));
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 1.0F));
        try {
            context.graphics.renderItem(entry.output(), Math.round(x + ITEM_X), Math.round(y + ITEM_Y));
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        float amountX = x + w - AMOUNT_RIGHT - amountW;
        int nameMax = Math.max(0, Math.round(amountX - contentX - TEXT_GAP));
        String name = fit(font, entry.output().getHoverName().getString(), nameMax);
        context.graphics.drawString(font, name, Math.round(contentX), Math.round(y + TEXT_Y), withAlpha(TEXT_PRIMARY, alpha), false);
        context.graphics.drawString(font, amount, Math.round(amountX), Math.round(y + TEXT_Y), withAlpha(TEXT_VALUE, alpha), false);
    }

    private void drawScrollbar(GUIContext context, float x, float y, float w, float h, int visibleRows) {
        if (entries.size() <= visibleRows || visibleRows <= 0 || h <= 0.0F) {
            return;
        }
        float thumbH = Math.max(10.0F, h * visibleRows / entries.size());
        float thumbY = y + (h - thumbH) * scrollOffset / Math.max(1.0F, entries.size() - visibleRows);
        fill(context, x, y, w, h, 0xAA17141E);
        fill(context, x, thumbY, w, thumbH, 0xFF8B83A0);
    }

    private static HoverTooltips taskTooltip(NECraftingTaskEntry entry) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.output()));
        lines.add(Component.translatable(entry.statusKey()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.neoecoae.crafting.task.amount", NEHostFormat.number(entry.outputAmount())));
        lines.add(Component.translatable("gui.neoecoae.crafting.task.crafts", NEHostFormat.number(entry.craftCount())));
        if (entry.totalTicks() > 0L) {
            lines.add(Component.translatable("gui.neoecoae.crafting.task.elapsed_time",
                entry.elapsedTimeText()).withStyle(ChatFormatting.AQUA));
        }
        return new HoverTooltips(lines, entry.output().getTooltipImage().orElse(null), null, entry.output());
    }

    private static int visibleRows(float height) {
        return Math.max(1, (int) Math.floor((height + ROW_GAP) / (ROW_H + ROW_GAP)));
    }

    private static int clampScroll(int value, int total, int visibleRows) {
        return Mth.clamp(value, 0, Math.max(0, total - visibleRows));
    }

    private static void drawCenteredText(GUIContext context, Component text, float x, float y, float width, int color) {
        context.graphics.drawString(context.mc.font, text, Math.round(x + (width - context.mc.font.width(text)) / 2.0F), Math.round(y), color, false);
    }

    private static void fill(GUIContext context, float x, float y, float w, float h, int color) {
        context.graphics.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    private static int withAlpha(int color, float alpha) {
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        int baseAlpha = (color >>> 24) & 0xFF;
        int outAlpha = Mth.clamp(Math.round(baseAlpha * clamped), 0, 255);
        return (outAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return font.width(text) <= maxWidth ? text : "";
        }
        int ellipsisWidth = font.width("...");
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        String trimmed = text;
        while (!trimmed.isEmpty() && font.width(trimmed) + ellipsisWidth > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    private static String taskEntryKey(NECraftingTaskEntry entry, int index) {
        return entry.id() == null || entry.id().isBlank() ? "task:" + index : entry.id();
    }

    private record Frame(NECraftingTaskEntry entry, float y, float alpha, boolean exiting) {
    }

    private static final class TaskCardAnimation {
        private NECraftingTaskEntry entry;
        private float y;
        private float targetY;
        private float alpha;
        private long lastUpdateMs;
        private boolean exiting;
        private long exitStartedMs;

        private TaskCardAnimation(NECraftingTaskEntry entry, float targetY, int entryOffset) {
            this.entry = entry;
            this.targetY = targetY;
            this.y = targetY + entryOffset;
            this.lastUpdateMs = Util.getMillis();
        }

        private void update(long nowMs) {
            long elapsed = Math.max(0L, Math.min(1000L, nowMs - lastUpdateMs));
            lastUpdateMs = nowMs;
            float moveT = MOVE_MS <= 0L ? 1.0F : Mth.clamp((float) elapsed / (float) MOVE_MS, 0.0F, 1.0F);
            y += (targetY - y) * moveT;
            if (Math.abs(targetY - y) < 0.25F) {
                y = targetY;
            }
            if (exiting) {
                long fadeElapsed = Math.max(0L, nowMs - exitStartedMs);
                alpha = 1.0F - Mth.clamp((float) fadeElapsed / (float) FADE_MS, 0.0F, 1.0F);
            } else {
                float fadeStep = FADE_MS <= 0L ? 1.0F : Mth.clamp((float) elapsed / (float) FADE_MS, 0.0F, 1.0F);
                alpha = Math.min(1.0F, alpha + fadeStep);
            }
        }
    }
}
