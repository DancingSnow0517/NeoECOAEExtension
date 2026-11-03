package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.utils.ModularUITooltipComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

/**
 * Card-style statistic drawn inside the vanilla tooltip frame: header, large value, bar and detail rows.
 * The frame and placement stay vanilla; only the content is custom.
 */
public final class CraftingTooltipCard extends UIElement {
    public static final int ACCENT = 0xFFC9B8FF;
    public static final int WARN = 0xFFF0A848;
    public static final int OK = 0xFF8EE6A0;
    public static final int CYAN = 0xFF5BB8D0;
    public static final int PRIMARY = 0xFFF2EEF8;
    public static final int MUTED = 0xFF9C94AC;
    private static final int TRACK = 0xFF332E3B;
    private static final int DIVIDER = 0xFF37303F;
    private static final int MIN_WIDTH = 140;
    private static final int GAP = 12;
    private static final int VALUE_Y = 14;
    private static final int BAR_Y = 35;
    private static final int ROWS_Y = 43;
    private static final int ROW_STEP = 10;
    private static final int DIVIDER_STEP = 8;

    public enum Icon { NONE, CHIP, STACK }

    record Segment(double fraction, int color, boolean raised) {}

    private record Row(Component left, Component right, int rightColor, boolean divider) {}

    private final Font font;
    private final Icon icon;
    private final Component title;
    private final Component headerRight;
    private final int headerRightColor;
    private final Component value;
    private final Component suffix;
    private final int valueColor;
    private final Component status;
    private final int statusColor;
    private final List<Segment> segments;
    private final List<Row> rows;
    private final int width;

    private CraftingTooltipCard(Builder builder, Font font) {
        this.font = font;
        icon = builder.icon;
        title = upper(builder.title).withStyle(ChatFormatting.BOLD);
        headerRight = builder.headerRight;
        headerRightColor = builder.headerRightColor;
        value = builder.value;
        suffix = builder.suffix;
        valueColor = builder.valueColor;
        status = builder.status;
        statusColor = builder.statusColor;
        segments = List.copyOf(builder.segments);
        rows = List.copyOf(builder.rows);
        int content = Math.max(titleX() + font.width(title) + gap(headerRight) + font.width(headerRight),
            font.width(value) * 2 + font.width(suffix) + (font.width(suffix) > 0 ? 1 : 0)
                + gap(status) + font.width(status));
        int height = BAR_Y + 3;
        int y = ROWS_Y;
        for (Row row : rows) {
            if (row.divider()) y += DIVIDER_STEP;
            content = Math.max(content, font.width(row.left()) + gap(row.right()) + font.width(row.right()));
            height = y + font.lineHeight;
            y += ROW_STEP;
        }
        width = Math.max(MIN_WIDTH, content);
        int w = width, h = height;
        layout(layout -> layout.width(w).height(h).paddingAll(0));
    }

    public static Builder builder(Icon icon, Component title) {
        return new Builder(icon, title);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        var graphics = context.graphics;
        int x = Math.round(getPositionX()), y = Math.round(getPositionY());

        drawIcon(context, x, y);
        graphics.drawString(font, title, x + titleX(), y, ACCENT, true);
        right(context, headerRight, x, y, headerRightColor);

        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y + VALUE_Y, 0);
            pose.scale(2.0F, 2.0F, 1.0F);
            graphics.drawString(font, value, 0, 0, valueColor, true);
        } finally {
            pose.popPose();
        }
        int baseline = y + VALUE_Y + font.lineHeight - 1;
        graphics.drawString(font, suffix, x + font.width(value) * 2 + 1, baseline, MUTED, true);
        right(context, status, x, baseline, statusColor);

        drawBar(context, x, y + BAR_Y);
        int rowY = y + ROWS_Y;
        for (Row row : rows) {
            if (row.divider()) {
                graphics.fill(x, rowY + 3, x + width, rowY + 4, DIVIDER);
                rowY += DIVIDER_STEP;
            }
            graphics.drawString(font, row.left(), x, rowY, PRIMARY, true);
            right(context, row.right(), x, rowY, row.rightColor());
            rowY += ROW_STEP;
        }
    }

    private void drawBar(GUIContext context, int x, int y) {
        var graphics = context.graphics;
        graphics.fill(x, y, x + width, y + 3, TRACK);
        int start = 0;
        for (Segment segment : segments) {
            int length = (int) Math.round(width * Mth.clamp(segment.fraction(), 0.0D, 1.0D));
            if (segment.raised() && segment.fraction() > 0.0D) length = Math.max(3, length);
            int end = Math.min(width, start + length);
            if (end > start) {
                graphics.fill(x + start, y - (segment.raised() ? 1 : 0), x + end, y + (segment.raised() ? 4 : 3),
                    segment.color());
            }
            start = end;
        }
    }

    private int titleX() {
        return icon == Icon.NONE ? 0 : 11;
    }

    private void drawIcon(GUIContext context, int x, int y) {
        if (icon == Icon.NONE) return;
        var graphics = context.graphics;
        if (icon == Icon.STACK) {
            graphics.fill(x + 1, y + 1, x + 8, y + 3, ACCENT);
            graphics.fill(x, y + 4, x + 9, y + 5, ACCENT);
            graphics.fill(x + 1, y + 6, x + 8, y + 8, ACCENT);
            return;
        }
        graphics.fill(x + 1, y + 1, x + 8, y + 2, ACCENT);
        graphics.fill(x + 1, y + 7, x + 8, y + 8, ACCENT);
        graphics.fill(x + 1, y + 2, x + 2, y + 7, ACCENT);
        graphics.fill(x + 7, y + 2, x + 8, y + 7, ACCENT);
        graphics.fill(x + 3, y + 3, x + 6, y + 6, ACCENT);
        graphics.fill(x + 4, y, x + 5, y + 1, ACCENT);
        graphics.fill(x + 4, y + 8, x + 5, y + 9, ACCENT);
        graphics.fill(x, y + 4, x + 1, y + 5, ACCENT);
        graphics.fill(x + 8, y + 4, x + 9, y + 5, ACCENT);
    }

    private void right(GUIContext context, Component text, int x, int y, int color) {
        int textWidth = font.width(text);
        if (textWidth > 0) context.graphics.drawString(font, text, x + width - textWidth, y, color, true);
    }

    private int gap(Component right) {
        return font.width(right) == 0 ? 0 : GAP;
    }

    public static MutableComponent upper(Component text) {
        return Component.literal(text.getString().toUpperCase(Locale.ROOT));
    }

    public static MutableComponent label(String key, String value, int valueColor) {
        return Component.translatable(key).withStyle(style -> style.withColor(MUTED)).append(Component.literal(" " + value).withStyle(style -> style.withColor(valueColor)));
    }

    public static String group(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static final class Builder {
        private final Icon icon;
        private final Component title;
        private Component headerRight = Component.empty();
        private int headerRightColor = WARN;
        private Component value = Component.empty();
        private Component suffix = Component.empty();
        private int valueColor = PRIMARY;
        private Component status = Component.empty();
        private int statusColor = WARN;
        private final List<Segment> segments = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();

        private Builder(Icon icon, Component title) {
            this.icon = icon;
            this.title = title;
        }

        public Builder headerRight(Component text, int color) {
            headerRight = text;
            headerRightColor = color;
            return this;
        }

        public Builder value(String main, String total) {
            value = Component.literal(main);
            suffix = total == null ? Component.empty() : Component.literal(" / " + total);
            return this;
        }

        public Builder valueColor(int color) {
            valueColor = color;
            return this;
        }

        public Builder status(Component text, int color) {
            status = upper(text);
            statusColor = color;
            return this;
        }

        public Builder segment(double fraction, int color, boolean raised) {
            segments.add(new Segment(fraction, color, raised));
            return this;
        }

        public Builder row(Component left, Component right, int rightColor) {
            rows.add(new Row(left, right, rightColor, false));
            return this;
        }

        public Builder dividedRow(Component left, Component right, int rightColor) {
            rows.add(new Row(left, right, rightColor, true));
            return this;
        }

        public ModularUITooltipComponent build() {
            return new ModularUITooltipComponent(new CraftingTooltipCard(this, Minecraft.getInstance().font));
        }
    }
}
