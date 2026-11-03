package cn.dancingsnow.neoecoae.gui.crafting;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.utils.ModularUITooltipComponent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Row-based card drawn inside the vanilla tooltip frame: header, dividers, section labels, icon rows,
 * indented notes and progress bars. The frame and placement stay vanilla; only the content is custom.
 */
public final class CraftingListCard extends UIElement {
    public static final int DANGER = 0xFFFF7A7A;
    private static final int TRACK = 0xFF332E3B;
    private static final int DIVIDER = 0xFF37303F;
    private static final int LINE = 9;
    /** Height of a row whose value is drawn at {@link CraftingTooltipCard#LARGE_SCALE}. */
    private static final int LARGE_HEIGHT = 15;
    private static final int MIN_WIDTH = 150;
    private static final int GAP = 12;
    private static final int ICON_SPACE = 12;
    private static final int GLYPH_SPACE = 9;
    private static final int ICON_BOX = 20;
    private static final int ICON_BACKGROUND = 0xFF2E2640;
    private static final int WRAP = 220;

    /** Pixel glyphs, one string per row, '#' marks a filled pixel. */
    public enum Glyph {
        DOTS(".##..##.", "########", "########", ".##..##."),
        STACK(".#######.", ".#######.", ".........", "#########", ".........", ".#######.", ".#######."),
        BOLT("....##", "...##.", "..##..", ".#####", "...##.", "..##..", ".##..."),
        SNOW("...#...", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "...#..."),
        DROP("...#...", "..###..", ".#####.", "#######", "#######", ".#####.", "..###.."),
        NODES("..###..", "..###..", "...#...", ".#####.", ".#...#.", "###.###", "###.###"),
        CROSS("#...#", ".#.#.", "..#..", ".#.#.", "#...#"),
        CHECK("......#", ".....#.", "#...#..", ".#.#...", "..#...."),
        CHIP(".#.#.#.", "#######", "##...##", "##.#.##", "##...##", "#######", ".#.#.#."),
        CUBE("...#...", ".##.##.", "#.....#", "##...##", "#.#.#.#", "#..#..#", ".#.#.#.", "...#..."),
        STEPS("###....", "#.#....", "###....", ".#.....", ".#.###.", ".##.#.#", "....###"),
        INFO("..###..", ".#...#.", "#..#..#", "#.....#", "#..#..#", ".#.#.#.", "..###.."),
        POINTER(".#.....", ".##....", ".###...", ".####..", ".#####.", ".##....", "...#..."),
        BOOK("##.##..", "#.#.#..", "#.#.#..", "#.#.#..", "#.#.#..", "##.##..", "..#....");

        private final String[] rows;

        Glyph(String... rows) {
            this.rows = rows;
        }

        int width() {
            int width = 0;
            for (String row : rows) width = Math.max(width, row.length());
            return width;
        }

        void draw(GuiGraphics graphics, int x, int y, int color) {
            int top = y + (LINE - 1 - rows.length) / 2;
            for (int row = 0; row < rows.length; row++) {
                String pixels = rows[row];
                int start = -1;
                for (int column = 0; column <= pixels.length(); column++) {
                    boolean filled = column < pixels.length() && pixels.charAt(column) == '#';
                    if (filled && start < 0) start = column;
                    if (!filled && start >= 0) {
                        graphics.fill(x + start, top + row, x + column, top + row + 1, color);
                        start = -1;
                    }
                }
            }
        }
    }

    /** Paints a 16x16 icon at the given position. */
    @FunctionalInterface
    public interface IconPainter {
        void paint(GuiGraphics graphics, int x, int y);
    }

    private interface Entry {
        int width(Font font);

        int height();

        void draw(GuiGraphics graphics, Font font, int x, int y, int width);
    }

    private record Header(Glyph glyph, Component title, Component right, int rightColor) implements Entry {
        Component line() {
            MutableComponent line = Component.empty().append(title.copy().withColor(CraftingTooltipCard.ACCENT));
            if (!right.getString().isEmpty()) line.append("  ").append(right.copy().withColor(rightColor));
            return line;
        }

        public int width(Font font) {
            return (glyph == null ? 0 : ICON_SPACE) + font.width(title) + gap(font, right) + font.width(right);
        }

        public int height() {
            return LINE + 1;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            int textX = x;
            if (glyph != null) {
                glyph.draw(graphics, x, y, CraftingTooltipCard.ACCENT);
                textX += ICON_SPACE;
            }
            graphics.drawString(font, title, textX, y, CraftingTooltipCard.ACCENT, true);
            drawRight(graphics, font, right, x + width, y, rightColor);
        }
    }

    private record Divider() implements Entry {
        public int width(Font font) {
            return 0;
        }

        public int height() {
            return 9;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            graphics.fill(x, y + 3, x + width, y + 4, DIVIDER);
        }
    }

    private record Section(Component label, Component right, int rightColor) implements Entry {
        public int width(Font font) {
            return font.width(label) + gap(font, right) + font.width(right);
        }

        public int height() {
            return LINE + 2;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            graphics.drawString(font, label, x, y, CraftingTooltipCard.MUTED, true);
            drawRight(graphics, font, right, x + width, y, rightColor);
        }
    }

    private record Row(Glyph glyph, int glyphColor, Component left, int leftColor, Glyph rightGlyph,
                       Component right, Component suffix, int rightColor, boolean large) implements Entry {
        public int width(Font font) {
            int rightWidth = (large ? (int) Math.ceil(font.width(right) * CraftingTooltipCard.LARGE_SCALE) : font.width(right)) + font.width(suffix)
                + (rightGlyph == null ? 0 : GLYPH_SPACE);
            return (glyph == null ? 0 : ICON_SPACE) + font.width(left) + (rightWidth == 0 ? 0 : GAP) + rightWidth;
        }

        public int height() {
            return large ? LARGE_HEIGHT : LINE + 2;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            int textY = large ? y + 3 : y;
            int textX = x;
            if (glyph != null) {
                glyph.draw(graphics, x, textY, glyphColor);
                textX += ICON_SPACE;
            }
            graphics.drawString(font, left, textX, textY, leftColor, true);
            int end = x + width;
            int suffixWidth = font.width(suffix);
            if (suffixWidth > 0) {
                graphics.drawString(font, suffix, end - suffixWidth, textY + (large ? 1 : 0), CraftingTooltipCard.MUTED, true);
                end -= suffixWidth;
            }
            int rightWidth = font.width(right);
            if (rightWidth > 0) {
                if (large) {
                    int start = end - (int) Math.ceil(rightWidth * CraftingTooltipCard.LARGE_SCALE);
                    var pose = graphics.pose();
                    pose.pushPose();
                    try {
                        pose.translate(start, y, 0);
                        pose.scale(CraftingTooltipCard.LARGE_SCALE, CraftingTooltipCard.LARGE_SCALE, 1.0F);
                        graphics.drawString(font, right, 0, 0, rightColor, true);
                    } finally {
                        pose.popPose();
                    }
                    end = start;
                } else {
                    graphics.drawString(font, right, end - rightWidth, textY, rightColor, true);
                    end -= rightWidth;
                }
            }
            if (rightGlyph != null) {
                int glyphX = end - (rightWidth > 0 ? 3 : 0) - rightGlyph.width();
                rightGlyph.draw(graphics, glyphX, textY, rightColor);
            }
        }
    }

    private record Note(Component text, int color) implements Entry {
        public int width(Font font) {
            return ICON_SPACE + font.width(text);
        }

        public int height() {
            return LINE + 2;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            graphics.drawString(font, text, x + ICON_SPACE, y, color, true);
        }
    }

    private record Bar(double fraction, int color, int track) implements Entry {
        public int width(Font font) {
            return 0;
        }

        public int height() {
            return 7;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            graphics.fill(x, y + 1, x + width, y + 4, track);
            int length = (int) Math.round(width * Mth.clamp(fraction, 0.0D, 1.0D));
            if (length > 0) graphics.fill(x, y + 1, x + length, y + 4, color);
        }
    }

    private record IconHeader(IconPainter icon, Component title, Component subtitle) implements Entry {
        public int width(Font font) {
            return ICON_BOX + 5 + Math.max(font.width(title), font.width(subtitle));
        }

        public int height() {
            return ICON_BOX + 2;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            graphics.fill(x, y, x + ICON_BOX, y + ICON_BOX, ICON_BACKGROUND);
            icon.paint(graphics, x + 2, y + 2);
            int textX = x + ICON_BOX + 5;
            boolean twoLines = font.width(subtitle) > 0;
            graphics.drawString(font, title, textX, twoLines ? y + 1 : y + (ICON_BOX - LINE) / 2 + 1,
                CraftingTooltipCard.PRIMARY, true);
            if (twoLines) graphics.drawString(font, subtitle, textX, y + LINE + 2, CraftingTooltipCard.MUTED, true);
        }
    }

    /** Free text with an optional right column; long left text wraps at {@link #WRAP}. */
    private record Text(Component left, Component right, int color) implements Entry {
        public int width(Font font) {
            int rightWidth = font.width(right);
            return Math.min(WRAP, font.width(left) + (rightWidth == 0 ? 0 : GAP + rightWidth));
        }

        public int height() {
            return LINE + 2;
        }

        private List<FormattedCharSequence> lines(Font font, int width) {
            int rightWidth = Math.min(font.width(right), width / 2);
            List<FormattedCharSequence> lines = font.split(left,
                Math.max(1, width - (rightWidth == 0 ? 0 : GAP + rightWidth)));
            return lines == null ? List.of() : lines;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            var lines = lines(font, width);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), x, y + i * (LINE + 2), color, true);
            }
            drawRight(graphics, font, right, x + width, y, color);
        }
    }

    private final Font font;
    private final List<Entry> entries;
    private final int width;
    private final int[] heights;

    private CraftingListCard(List<Entry> entries, Font font) {
        this.font = font;
        this.entries = List.copyOf(entries);
        int content = MIN_WIDTH;
        for (Entry entry : this.entries) content = Math.max(content, entry.width(font));
        width = content;
        heights = new int[this.entries.size()];
        int height = 0;
        for (int i = 0; i < heights.length; i++) {
            Entry entry = this.entries.get(i);
            heights[i] = entry instanceof Text text
                ? Math.max(1, text.lines(font, width).size()) * (LINE + 2) : entry.height();
            height += heights[i];
        }
        int w = (int) Math.ceil(width * CraftingTooltipCard.CONTENT_SCALE), h = (int) Math.ceil((Math.max(0, height - 2)) * CraftingTooltipCard.CONTENT_SCALE);
        layout(layout -> layout.width(w).height(h).paddingAll(0));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        var pose = context.graphics.pose();
        pose.pushPose();
        try {
            pose.translate(Math.round(getPositionX()), Math.round(getPositionY()), 0);
            pose.scale(CraftingTooltipCard.CONTENT_SCALE, CraftingTooltipCard.CONTENT_SCALE, 1.0F);
            drawEntries(context);
        } finally {
            pose.popPose();
        }
    }

    /** Draws the content with its top-left corner at the origin, before content scaling. */
    private void drawEntries(GUIContext context) {
        int x = 0, y = 0;
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).draw(context.graphics, font, x, y, width);
            y += heights[i];
        }
    }

    private static void drawRight(GuiGraphics graphics, Font font, Component text, int end, int y, int color) {
        int textWidth = font.width(text);
        if (textWidth > 0) graphics.drawString(font, text, end - textWidth, y, color, true);
    }

    private static int gap(Font font, Component right) {
        return font.width(right) == 0 ? 0 : GAP;
    }

    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();

        private Builder() {}

        public Builder header(Glyph glyph, Component title, Component right, int rightColor) {
            entries.add(new Header(glyph, title.copy().withStyle(ChatFormatting.BOLD), right, rightColor));
            return this;
        }

        public Builder divider() {
            entries.add(new Divider());
            return this;
        }

        public Builder section(Component label) {
            entries.add(new Section(label, Component.empty(), CraftingTooltipCard.PRIMARY));
            return this;
        }

        public Builder section(Component label, Component right, int rightColor) {
            entries.add(new Section(label, right, rightColor));
            return this;
        }

        /** A labelled row with a right-aligned value. */
        public Builder row(Glyph glyph, int glyphColor, Component left, Component right, int rightColor) {
            entries.add(new Row(glyph, glyphColor, left, CraftingTooltipCard.PRIMARY, null, right, Component.empty(),
                rightColor, false));
            return this;
        }

        /** A labelled row whose right side is a status glyph followed by optional text. */
        public Builder statusRow(Glyph glyph, Component left, boolean ok, Component text) {
            int color = ok ? CraftingTooltipCard.OK : DANGER;
            entries.add(new Row(glyph, CraftingTooltipCard.MUTED, left, CraftingTooltipCard.PRIMARY,
                ok ? Glyph.CHECK : Glyph.CROSS, text, Component.empty(), color, false));
            return this;
        }

        /** A row whose leading icon is the pass/fail glyph itself. */
        public Builder checkRow(boolean ok, Component left) {
            entries.add(new Row(ok ? Glyph.CHECK : Glyph.CROSS, ok ? CraftingTooltipCard.OK : DANGER, left,
                CraftingTooltipCard.PRIMARY, null, Component.empty(), Component.empty(), CraftingTooltipCard.PRIMARY, false));
            return this;
        }

        /** A row with a double-size right value and a muted suffix such as {@code / 11}. */
        public Builder largeRow(Glyph glyph, Component left, String value, String suffix, int valueColor) {
            entries.add(new Row(glyph, CraftingTooltipCard.MUTED, left, CraftingTooltipCard.PRIMARY, null,
                Component.literal(value), suffix == null ? Component.empty() : Component.literal(" " + suffix),
                valueColor, true));
            return this;
        }

        public Builder iconHeader(IconPainter icon, Component title, Component subtitle) {
            entries.add(new IconHeader(icon, title.copy().withStyle(ChatFormatting.BOLD), subtitle));
            return this;
        }

        public Builder text(Component left, Component right, int color) {
            entries.add(new Text(left, right, color));
            return this;
        }

        /** A row with a leading muted glyph and muted text, used for hints. */
        public Builder hint(Glyph glyph, Component text) {
            entries.add(new Row(glyph, CraftingTooltipCard.MUTED, text, CraftingTooltipCard.MUTED, null,
                Component.empty(), Component.empty(), CraftingTooltipCard.MUTED, false));
            return this;
        }

        /** A row with a double-size coloured right value and no glyph. */
        public Builder largeValue(Component left, String value, int valueColor) {
            entries.add(new Row(null, CraftingTooltipCard.MUTED, left, CraftingTooltipCard.PRIMARY, null,
                Component.literal(value), Component.empty(), valueColor, true));
            return this;
        }

        public Builder note(Component text, int color) {
            entries.add(new Note(text, color));
            return this;
        }

        public Builder bar(double fraction, int color) {
            return bar(fraction, color, TRACK);
        }

        public Builder bar(double fraction, int color, int track) {
            entries.add(new Bar(fraction, color, track));
            return this;
        }

        /** LDLib2 2.2.x needs a vanilla text line: the leading header becomes that line, the rest is the card. */
        public List<Object> build() {
            Font font = Minecraft.getInstance().font;
            List<Entry> body = new ArrayList<>(entries);
            Component title = Component.empty();
            if (!body.isEmpty() && body.get(0) instanceof Header header) {
                title = header.line();
                body.remove(0);
                if (!body.isEmpty() && body.get(0) instanceof Divider) body.remove(0);
            } else if (!body.isEmpty() && body.get(0) instanceof IconHeader header) {
                title = header.title();
                body.set(0, new IconHeader(header.icon(), header.subtitle(), Component.empty()));
            }
            return List.of(title, new ModularUITooltipComponent(new CraftingListCard(body, font)));
        }
    }

    /** Hover tooltip for a built card: the title line plus the card component. */
    public static HoverTooltips hover(List<Object> card) {
        List<Component> lines = new ArrayList<>();
        TooltipComponent component = null;
        for (Object entry : card) {
            if (entry instanceof Component text) lines.add(text);
            else if (entry instanceof TooltipComponent tooltip) component = tooltip;
        }
        return new HoverTooltips(lines, component, null, null);
    }
}
