package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.gui.NETextures;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class NEHostTaskListElement extends UIElement {
    private static final int CARD_HEIGHT = 18;
    private static final int CARD_GAP = 2;
    private final Supplier<List<NECraftingTaskEntry>> entries;
    private int scrollOffset;

    public NEHostTaskListElement(Supplier<List<NECraftingTaskEntry>> entries) {
        this.entries = entries;
        layout(layout -> layout.width(184).height(96));
        style(style -> style.backgroundTexture(NETextures.CARD_BACKGROUND));
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            NECraftingTaskEntry entry = entryAt(event.x, event.y);
            if (entry != null) {
                event.hoverTooltips = new HoverTooltips(tooltip(entry), null, null, null);
            }
        });
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        float x = getContentX() + 4;
        float y = getContentY() + 4;
        float width = getContentWidth() - 8;
        int visible = visibleRows();
        List<NECraftingTaskEntry> list = entries.get();
        scrollOffset = clamp(scrollOffset, list.size(), visible);
        NEHostDraw.text(context, Component.translatable("gui.neoecoae.crafting.tasks"), x, y, NEHostDraw.TEXT);
        NEHostDraw.rightText(context, NEHostFormat.number(list.size()), x + width, y, NEHostDraw.TEXT);
        y += 13;
        if (list.isEmpty()) {
            NEHostDraw.centeredText(context, Component.translatable("gui.neoecoae.crafting.no_tasks"),
                x, y + 24, width, NEHostDraw.MUTED);
            return;
        }
        int count = Math.min(visible, list.size() - scrollOffset);
        for (int i = 0; i < count; i++) {
            drawCard(context, list.get(scrollOffset + i), x, y + i * (CARD_HEIGHT + CARD_GAP), width);
        }
        drawScrollbar(context, list.size(), visible, x + width - 3, y);
    }

    private void drawCard(GUIContext context, NECraftingTaskEntry entry, float x, float y, float width) {
        int color = statusColor(entry.status());
        NEHostDraw.rect(context, x, y, width, CARD_HEIGHT, 0xffd8d3e4);
        NEHostDraw.rect(context, x + 1, y + 1, width - 2, CARD_HEIGHT - 2, 0xff2c2735);
        NEHostDraw.rect(context, x + 2, y + CARD_HEIGHT - 3, width - 4, 1, color);
        NEHostDraw.item(context, entry.output(), x + 1, y + 1);
        String amount = "x" + NEHostFormat.number(entry.outputAmount());
        int amountWidth = context.mc.font.width(amount);
        String name = NEHostDraw.fit(context, entry.output().getHoverName().getString(), Math.max(8, Math.round(width) - 30 - amountWidth));
        NEHostDraw.text(context, name, x + 21, y + 5, 0xffffffff);
        NEHostDraw.rightText(context, amount, x + width - 5, y + 5, 0xffdff7ff);
        long done = Math.max(0L, entry.totalTicks() - entry.remainingTicks());
        int fill = entry.status() == NECraftingTaskEntry.Status.WAITING_OUTPUT
            ? Math.round(width - 26)
            : NEHostDraw.ratioWidth(done, entry.totalTicks(), Math.round(width - 26));
        NEHostDraw.rect(context, x + 21, y + CARD_HEIGHT - 5, width - 26, 2, 0xaa17141e);
        if (fill > 0) {
            NEHostDraw.rect(context, x + 21, y + CARD_HEIGHT - 5, fill, 2, color);
        }
    }

    private void drawScrollbar(GUIContext context, int total, int visible, float x, float y) {
        if (total <= visible) {
            return;
        }
        float height = visible * CARD_HEIGHT + Math.max(0, visible - 1) * CARD_GAP;
        float thumbHeight = Math.max(10, height * visible / total);
        float maxOffset = Math.max(1, total - visible);
        float thumbY = y + (height - thumbHeight) * scrollOffset / maxOffset;
        NEHostDraw.rect(context, x, y, 2, height, 0xaa17141e);
        NEHostDraw.rect(context, x, thumbY, 2, thumbHeight, 0xff8b83a0);
    }

    private void onMouseWheel(UIEvent event) {
        int visible = visibleRows();
        int total = entries.get().size();
        if (total <= visible) {
            return;
        }
        scrollOffset = clamp(scrollOffset + (event.deltaY < 0 ? 1 : -1), total, visible);
        event.stopPropagation();
    }

    private NECraftingTaskEntry entryAt(double mouseX, double mouseY) {
        float x = getContentX() + 4;
        float y = getContentY() + 17;
        float width = getContentWidth() - 8;
        List<NECraftingTaskEntry> list = entries.get();
        int visible = visibleRows();
        scrollOffset = clamp(scrollOffset, list.size(), visible);
        int count = Math.min(visible, list.size() - scrollOffset);
        for (int i = 0; i < count; i++) {
            float cardY = y + i * (CARD_HEIGHT + CARD_GAP);
            if (NEHostDraw.contains(x, cardY, width, CARD_HEIGHT, mouseX, mouseY)) {
                return list.get(scrollOffset + i);
            }
        }
        return null;
    }

    private int visibleRows() {
        int available = Math.max(0, Math.round(getContentHeight()) - 21);
        return Math.max(1, (available + CARD_GAP) / (CARD_HEIGHT + CARD_GAP));
    }

    private static int clamp(int value, int total, int visible) {
        return Mth.clamp(value, 0, Math.max(0, total - visible));
    }

    private static int statusColor(NECraftingTaskEntry.Status status) {
        return switch (status) {
            case RUNNING -> NEHostDraw.SUCCESS;
            case QUEUED -> NEHostDraw.WARNING;
            case WAITING_OUTPUT -> NEHostDraw.BLUE;
        };
    }

    private static List<Component> tooltip(NECraftingTaskEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(entry.output().getHoverName());
        lines.add(Component.translatable(statusKey(entry.status())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.neoecoae.crafting.task.amount", NEHostFormat.number(entry.outputAmount())));
        if (entry.craftCount() > 1) {
            lines.add(Component.translatable("gui.neoecoae.crafting.task.crafts", NEHostFormat.number(entry.craftCount())));
        }
        if (entry.totalTicks() > 0) {
            long done = Math.max(0L, entry.totalTicks() - entry.remainingTicks());
            lines.add(Component.literal(NEHostFormat.percent(done, entry.totalTicks())).withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    private static String statusKey(NECraftingTaskEntry.Status status) {
        return switch (status) {
            case RUNNING -> "gui.neoecoae.crafting.task.status.running";
            case QUEUED -> "gui.neoecoae.crafting.task.status.queued";
            case WAITING_OUTPUT -> "gui.neoecoae.crafting.task.status.waiting_output";
        };
    }
}
