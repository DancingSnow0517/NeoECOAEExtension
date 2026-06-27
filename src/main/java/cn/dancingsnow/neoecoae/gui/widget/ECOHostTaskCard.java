package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.host.NECraftingTaskEntry;
import cn.dancingsnow.neoecoae.gui.host.NEHostFormat;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * A single crafting-task card: item icon, name, output amount and a status-colored progress bar.
 * It is a flex-positioned {@link UIElement} (one row in a {@link ECOHostTaskList} scroller); the
 * visual is painted in {@link #drawBackgroundAdditional} from the element's own geometry rather than
 * from hardcoded coordinates, and the hover tooltip is wired through the normal event system.
 */
public class ECOHostTaskCard extends UIElement {
    private static final int TEXT_PRIMARY = 0xFFD6D0E0;
    private static final int TEXT_VALUE = 0xFF8377FF;
    private static final int FRAME_EDGE = 0xFFD8D3E4;
    private static final int FRAME_LAYER_1 = 0xFF121016;
    private static final int FRAME_LAYER_2 = 0xFF4D4855;
    private static final int FRAME_FACE = 0xFF2C2735;
    private static final int TRACK = 0xAA17141E;
    private static final long FADE_MS = 360L;
    private static final long MOVE_MS = 140L;
    private static final int ITEM_X = 2;
    private static final int ITEM_Y = 1;
    private static final int CONTENT_X = 19;
    private static final int TEXT_Y = 3;
    private static final int AMOUNT_RIGHT = 5;
    private static final int TEXT_GAP = 6;
    private static final int MIN_NAME_WIDTH = 16;
    private static final int MIN_AMOUNT_WIDTH = 10;
    private static final int PROGRESS_RIGHT = 10;
    private static final int PROGRESS_BOTTOM = 3;
    private static final int PROGRESS_HEIGHT = 2;
    private static final int STATUS_INSET_X = 3;
    private static final int STATUS_BOTTOM = 2;
    private static final int STATUS_HEIGHT = 1;

    static final int CARD_HEIGHT = 18;

    private NECraftingTaskEntry entry;
    private String entryKey = "";
    private float animatedOffsetY;
    private float alpha = 1.0F;
    private long lastUpdateMs;

    public ECOHostTaskCard() {
        addClass("eco-host-task-card");
        layout(layout -> layout.widthPercent(100).height(CARD_HEIGHT));
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            HoverTooltips tooltip = tooltip();
            if (tooltip != null) {
                event.hoverTooltips = tooltip;
            }
        });
    }

    public ECOHostTaskCard setEntry(NECraftingTaskEntry entry, int index) {
        String nextKey = taskEntryKey(entry, index);
        if (!nextKey.equals(entryKey)) {
            entryKey = nextKey;
            alpha = 0.0F;
            animatedOffsetY = CARD_HEIGHT;
            lastUpdateMs = Util.getMillis();
        }
        this.entry = entry;
        return this;
    }

    public ECOHostTaskCard clearEntry() {
        this.entry = null;
        this.entryKey = "";
        return this;
    }

    public NECraftingTaskEntry entry() {
        return entry;
    }

    public HoverTooltips tooltip() {
        if (entry == null) {
            return null;
        }
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

    public boolean containsWorldPoint(float mouseX, float mouseY) {
        return UIElement.isMouseOverRect(
            getPositionX(),
            getPositionY(),
            getSizeWidth(),
            getSizeHeight(),
            mouseX,
            mouseY
        );
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        if (entry == null) {
            return;
        }
        updateAnimation();
        float x = getPositionX();
        float y = getPositionY() + animatedOffsetY;
        float w = getSizeWidth();
        float h = getSizeHeight();
        int color = entry.statusColor();

        // recessed card frame
        fill(guiContext, x, y, w, h, withAlpha(FRAME_EDGE, alpha));
        fill(guiContext, x + 1, y + 1, w - 2, h - 2, withAlpha(FRAME_LAYER_1, alpha));
        fill(guiContext, x + 2, y + 2, w - 4, h - 4, withAlpha(FRAME_LAYER_2, alpha));
        fill(guiContext, x + 3, y + 3, w - 6, h - 6, withAlpha(FRAME_FACE, alpha));

        // status line along the bottom
        fill(guiContext, x + STATUS_INSET_X, y + h - STATUS_BOTTOM, w - STATUS_INSET_X * 2, STATUS_HEIGHT, withAlpha(color, alpha));

        // progress bar
        float contentX = x + CONTENT_X;
        float progressY = y + h - PROGRESS_BOTTOM;
        int progressW = Math.max(12, Math.round(w) - CONTENT_X - PROGRESS_RIGHT);
        fill(guiContext, contentX, progressY, progressW, PROGRESS_HEIGHT, withAlpha(TRACK, alpha));
        int fillW = entry.progressWidth(progressW);
        if (fillW > 0) {
            fill(guiContext, contentX, progressY, fillW, PROGRESS_HEIGHT, withAlpha(color, alpha));
        }

        // item icon
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 1.0F));
        try {
            guiContext.graphics.renderItem(entry.output(), Math.round(x + ITEM_X), Math.round(y + ITEM_Y));
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        // name + amount
        Font font = guiContext.mc.font;
        int textRight = Math.round(x + w - AMOUNT_RIGHT);
        int availableTextW = Math.max(0, textRight - Math.round(contentX));
        int amountMax = amountMaxWidth(availableTextW);
        String amount = fit(font, "x" + NEHostFormat.number(entry.outputAmount()), amountMax);
        int amountW = font.width(amount);
        float amountX = textRight - amountW;
        int nameMax = Math.max(0, Math.round(amountX - contentX - TEXT_GAP));
        String name = fit(font, entry.output().getHoverName().getString(), nameMax);
        guiContext.graphics.drawString(font, name, Math.round(contentX), Math.round(y + TEXT_Y), withAlpha(TEXT_PRIMARY, alpha), false);
        guiContext.graphics.drawString(font, amount, Math.round(amountX), Math.round(y + TEXT_Y), withAlpha(TEXT_VALUE, alpha), false);
    }

    private void updateAnimation() {
        long now = Util.getMillis();
        if (lastUpdateMs == 0L) {
            lastUpdateMs = now;
        }
        long elapsed = Math.max(0L, Math.min(1000L, now - lastUpdateMs));
        lastUpdateMs = now;
        float fadeStep = FADE_MS <= 0L ? 1.0F : Mth.clamp((float) elapsed / (float) FADE_MS, 0.0F, 1.0F);
        alpha = Math.min(1.0F, alpha + fadeStep);
        float moveT = MOVE_MS <= 0L ? 1.0F : Mth.clamp((float) elapsed / (float) MOVE_MS, 0.0F, 1.0F);
        animatedOffsetY += (0.0F - animatedOffsetY) * moveT;
        if (Math.abs(animatedOffsetY) < 0.25F) {
            animatedOffsetY = 0.0F;
        }
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

    private static int amountMaxWidth(int availableTextW) {
        if (availableTextW <= MIN_AMOUNT_WIDTH) {
            return Math.max(0, availableTextW);
        }
        int maxAfterName = Math.max(MIN_AMOUNT_WIDTH, availableTextW - MIN_NAME_WIDTH - TEXT_GAP);
        return Math.min(availableTextW, maxAfterName);
    }

    private static void fill(GUIContext guiContext, float x, float y, float w, float h, int color) {
        guiContext.graphics.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    private static int withAlpha(int color, float alpha) {
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        int baseAlpha = (color >>> 24) & 0xFF;
        int outAlpha = Mth.clamp(Math.round(baseAlpha * clamped), 0, 255);
        return (outAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static String taskEntryKey(NECraftingTaskEntry entry, int index) {
        if (entry == null) {
            return "";
        }
        return entry.id() == null || entry.id().isBlank() ? "task:" + index : entry.id();
    }
}
