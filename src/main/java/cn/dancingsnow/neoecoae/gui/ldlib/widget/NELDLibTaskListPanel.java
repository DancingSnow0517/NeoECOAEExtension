package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibGuiRenderState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibScrollBar;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibTaskCards;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibTextRender;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

final class NELDLibTaskListPanel {
    private static final int SCROLLBAR_BACKGROUND = 0xAA17141E;
    private static final int SCROLLBAR_THUMB = 0xFF8B83A0;

    private NELDLibTaskListPanel() {}

    static int visibleCardCount(int cardY, int listBottomY, int cardH, int cardStride) {
        int space = listBottomY - cardY;
        if (space < cardH) {
            return 1;
        }
        return Math.max(1, 1 + (space - cardH) / cardStride);
    }

    static int clampScrollOffset(int value, int total, int cardY, int listBottomY, int cardH, int cardStride) {
        int visible = visibleCardCount(cardY, listBottomY, cardH, cardStride);
        return Mth.clamp(value, 0, Math.max(0, total - visible));
    }

    static void drawScrollbar(
            GuiGraphics g, int x, int y, int width, int height, int total, int visible, int scrollOffset) {
        if (total <= visible) {
            return;
        }
        NELDLibScrollBar.drawVertical(
                g,
                x,
                y,
                width,
                Math.max(1, height),
                total,
                visible,
                scrollOffset,
                SCROLLBAR_BACKGROUND,
                SCROLLBAR_THUMB,
                10);
    }

    static void drawCard(
            GuiGraphics g, Font font, NECraftingRecipeUiEntry entry, int x, int y, int w, int h, CardStyle style) {
        float alpha = Mth.clamp(style.alpha(), 0.0F, 1.0F);
        NELDLibTaskCards.drawCardRect(g, x, y, w, h, alpha, NELDLibTaskCards.statusColor(entry.status()));
        if (alpha > style.itemMinAlpha() && !entry.output().isEmpty()) {
            NELDLibGuiRenderState.beginVanillaGuiItemBatch(g);
            try {
                NELDLibGuiRenderState.renderVanillaSlotItem(
                        g, font, entry.output(), x + style.itemX(), y + style.itemY(), "");
            } finally {
                NELDLibGuiRenderState.endVanillaGuiItemBatch(g);
            }
        }

        String amountText = "x" + NELDLibText.compactTaskAmount(entry.outputAmount());
        int amountW = textWidth(font, amountText, style.textScale());
        int maxNameW = Math.max(16, w - style.nameWidthPadding() - amountW);
        String name = style.textScale() == 1.0F
                ? NELDLibTextRender.fitWithEllipsis(
                        font, entry.output().getHoverName().getString(), maxNameW)
                : NELDLibTextRender.fitScaledWithEllipsis(
                        font, entry.output().getHoverName().getString(), maxNameW, style.textScale());
        drawText(
                g,
                font,
                name,
                x + style.textX(),
                y + style.textY(),
                withAlpha(NELDLibStyle.DARK_TEXT_PRIMARY, alpha),
                style.textScale());
        drawRightText(
                g,
                font,
                Component.literal(amountText),
                x + w - style.amountRightPadding(),
                y + style.amountY(),
                withAlpha(NELDLibStyle.DARK_TEXT_VALUE, alpha),
                style.textScale());
        NELDLibTaskCards.drawProgressBar(
                g,
                x + style.progressX(),
                y + style.progressY(),
                w - style.progressWidthPadding(),
                style.progressH(),
                entry,
                alpha);
    }

    static List<Component> tooltipLines(
            NECraftingRecipeUiEntry entry,
            boolean includeCraftCount,
            boolean includePercentProgress,
            @Nullable LongFunction<String> timeFormatter) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.output()));
        lines.add(Component.translatable(NELDLibTaskCards.statusKey(entry.status())));
        lines.add(Component.translatable(
                "gui.neoecoae.crafting.task.amount", NELDLibText.compactTaskAmount(entry.outputAmount())));
        if (includeCraftCount) {
            lines.add(Component.translatable(
                    "gui.neoecoae.crafting.task.crafts", NELDLibText.number(entry.craftCount())));
        }
        if (entry.totalTicks() > 0L) {
            long done = Math.max(0L, entry.totalTicks() - entry.remainingTicks());
            if (timeFormatter != null) {
                lines.add(Component.translatable(
                        "gui.neoecoae.crafting.task.time",
                        timeFormatter.apply(done),
                        timeFormatter.apply(entry.totalTicks())));
            } else if (includePercentProgress) {
                lines.add(Component.literal(NELDLibText.percentOrNA(done, entry.totalTicks())));
            }
        }
        return lines;
    }

    private static int textWidth(Font font, String text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    private static void drawRightText(
            GuiGraphics g, Font font, Component text, int rightX, int y, int color, float scale) {
        drawText(g, font, text, rightX - Math.round(font.width(text) * scale), y, color, scale);
    }

    private static void drawText(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
        drawText(g, font, Component.literal(text), x, y, color, scale);
    }

    private static void drawText(GuiGraphics g, Font font, Component text, int x, int y, int color, float scale) {
        if (scale == 1.0F) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static int withAlpha(int color, float alpha) {
        return NELDLibTaskCards.withAlpha(color, alpha);
    }

    record CardStyle(
            int itemX,
            int itemY,
            int textX,
            int textY,
            int amountY,
            int amountRightPadding,
            int progressX,
            int progressY,
            int progressWidthPadding,
            int progressH,
            int nameWidthPadding,
            float textScale,
            float alpha,
            float itemMinAlpha) {}
}
