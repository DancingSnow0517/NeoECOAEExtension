package cn.dancingsnow.neoecoae.gui.ldlib.support;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class NELDLibValueText {
    private NELDLibValueText() {}

    public static void drawUsedTotal(
            GuiGraphics graphics,
            Font font,
            String prefix,
            String usedText,
            String maxText,
            long used,
            long max,
            String suffix,
            int x,
            int y) {
        int cursor = NELDLibClientStyle.drawSegment(graphics, font, prefix, x, y, NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibClientStyle.drawSegment(
                graphics, font, usedText, x + cursor, y, NELDLibStyle.usedValueColor(used, max));
        cursor += NELDLibClientStyle.drawSegment(graphics, font, " / ", x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibClientStyle.drawSegment(graphics, font, maxText, x + cursor, y, NELDLibStyle.DARK_TEXT_VALUE);
        if (!suffix.isEmpty()) {
            NELDLibClientStyle.drawSegment(graphics, font, " " + suffix, x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
        }
    }

    public static Component usedTotalComponent(
            String prefix, String usedText, String maxText, long used, long max, String suffix) {
        MutableComponent line =
                Component.literal(prefix).withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_MUTED));
        line.append(Component.literal(usedText)
                .withStyle(style -> style.withColor(NELDLibStyle.usedValueColor(used, max))));
        line.append(Component.literal(" / ").withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_MUTED)));
        line.append(Component.literal(maxText).withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_VALUE)));
        if (!suffix.isEmpty()) {
            line.append(
                    Component.literal(" " + suffix).withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_MUTED)));
        }
        return line;
    }
}
