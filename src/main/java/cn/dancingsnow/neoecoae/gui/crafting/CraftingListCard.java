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
    private static final int BANNER_PAD = 4;
    private static final int TILE_PAD = 4;
    private static final int TILE_GAP = 5;
    private static final int TILE_BORDER = 0xFF4A4058;
    private static final int TILE_BACKGROUND = 0xFF241D30;
    private static final int TILE_ACTIVE = 0xFF33284A;
    /** Headline values of a metric block, relative to the card content. */
    private static final float METRIC_SCALE = 2.0F;
    public static final int WARN_BACKGROUND = 0xFF3A2C1E;
    public static final int DANGER_BACKGROUND = 0xFF3A2026;

    /** Pixel glyphs, one string per row, '#' marks a filled pixel. */
    public enum Glyph {
        SPARKLE("...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."),
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
        BOOK("##.##..", "#.#.#..", "#.#.#..", "#.#.#..", "#.#.#..", "##.##..", "..#...."),
        THERMOMETER(".#.", "#.#", "#.#", "#.#", "#.#", "###", "###", ".#."),
        PAUSE(".###.", "#####", "#.#.#", "#.#.#", "#####", ".###."),
        WARNING("...#...", "..#.#..", "..###..", ".##.##.", ".#####.", "##.#.##", "#######"),
        BATTERY("#########.", "#.......##", "#.##.##.##", "#.##.##.##", "#.......##", "#########."),
        PUZZLE("..##...","..##...", "######.", "#....##", "#....##", "#.....#", "#######");

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

    private record Header(Glyph glyph, Component title, Component right, int rightColor, Glyph rightGlyph) implements Entry {
        Component line() {
            MutableComponent line = Component.empty().append(title.copy().withColor(CraftingTooltipCard.ACCENT));
            if (!right.getString().isEmpty()) line.append("  ").append(right.copy().withColor(rightColor));
            return line;
        }

        public int width(Font font) {
            return (glyph == null ? 0 : ICON_SPACE) + font.width(title) + gap(font, right) + font.width(right)
                + (rightGlyph == null ? 0 : GAP + GLYPH_SPACE);
        }

        public int height() {
            return LINE + 1;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            int textX = x;
            if (glyph != null) {
                glyph.draw(graphics, x, y, title.getStyle().getColor() == null
                    ? CraftingTooltipCard.ACCENT : title.getStyle().getColor().getValue() | 0xff000000);
                textX += ICON_SPACE;
            }
            graphics.drawString(font, title, textX, y, CraftingTooltipCard.ACCENT, true);
            drawRight(graphics, font, right, x + width, y, rightColor);
            if (rightGlyph != null) rightGlyph.draw(graphics, x + width - font.width(right) - GLYPH_SPACE, y, rightColor);
        }
    }

    private record Divider(int color) implements Entry {
        public int width(Font font) {
            return 0;
        }

        public int height() {
            return 9;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            graphics.fill(x, y + 3, x + width, y + 4, color);
        }
    }

    private record Space(int height) implements Entry {
        public int width(Font font) { return 0; }
        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {}
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

    /** Free text with an optional leading glyph and right column; long left text wraps at {@link #WRAP}. */
    private record Text(Glyph glyph, Component left, Component right, int color) implements Entry {
        private int indent() {
            return glyph == null ? 0 : ICON_SPACE;
        }

        public int width(Font font) {
            int rightWidth = font.width(right);
            return Math.min(WRAP, indent() + font.width(left) + (rightWidth == 0 ? 0 : GAP + rightWidth));
        }

        public int height() {
            return LINE + 2;
        }

        private List<FormattedCharSequence> lines(Font font, int width) {
            int rightWidth = Math.min(font.width(right), width / 2);
            List<FormattedCharSequence> lines = font.split(left,
                Math.max(1, width - indent() - (rightWidth == 0 ? 0 : GAP + rightWidth)));
            return lines == null ? List.of() : lines;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            var lines = lines(font, width);
            if (glyph != null) glyph.draw(graphics, x, y, color);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), x + indent(), y + i * (LINE + 2), color, true);
            }
            drawRight(graphics, font, right, x + width, y, color);
        }
    }

    /** Tinted callout box with a leading glyph, a title line and an optional muted detail line. */
    private record Banner(Glyph glyph, Component title, Component detail, int color, int background) implements Entry {
        public int width(Font font) {
            return BANNER_PAD * 2 + Math.max(ICON_SPACE + font.width(title), font.width(detail));
        }

        public int height() {
            return (hasDetail() ? 2 * (LINE + 2) : LINE + 1) + BANNER_PAD * 2 + 3;
        }

        private boolean hasDetail() {
            return !detail.getString().isEmpty();
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            int bottom = y + height() - 3;
            graphics.fill(x, y, x + width, bottom, background);
            int textY = y + BANNER_PAD + 1;
            glyph.draw(graphics, x + BANNER_PAD, textY, color);
            graphics.drawString(font, title, x + BANNER_PAD + ICON_SPACE, textY, color, true);
            if (hasDetail()) {
                graphics.drawString(font, detail, x + BANNER_PAD, textY + LINE + 2, CraftingTooltipCard.MUTED, true);
            }
        }
    }

    /**
     * A muted label above a double-size headline value, with an optional right column that is either another
     * label/headline pair or a small note on the headline line.
     */
    private record Metric(Component label, Component value, int valueColor, Component unit, int unitColor,
                          Component rightLabel, Component rightValue, int rightColor, boolean rightLarge,
                          boolean equalColumns) implements Entry {
        public int width(Font font) {
            int unitWidth = font.width(unit);
            int left = Math.max(font.width(label), scaled(font, value) + (unitWidth == 0 ? 0 : 3 + unitWidth));
            int right = Math.max(font.width(rightLabel), rightLarge ? scaled(font, rightValue) : font.width(rightValue));
            return equalColumns ? Math.max(left, right) * 2 + GAP : left + (right == 0 ? 0 : GAP) + right;
        }

        public int height() {
            return LINE + 2 + Math.round(LINE * METRIC_SCALE) + 2;
        }

        private static int scaled(Font font, Component text) {
            return (int) Math.ceil(font.width(text) * METRIC_SCALE);
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            graphics.drawString(font, label, x, y, CraftingTooltipCard.MUTED, true);
            int rightX = x + (width + GAP) / 2;
            if (equalColumns) graphics.drawString(font, rightLabel, rightX, y, CraftingTooltipCard.MUTED, true);
            else drawRight(graphics, font, rightLabel, x + width, y, CraftingTooltipCard.MUTED);
            int valueY = y + LINE + 2;
            drawScaled(graphics, font, value, x, valueY, valueColor);
            if (font.width(unit) > 0) {
                graphics.drawString(font, unit, x + scaled(font, value) + 3,
                    valueY + Math.round(LINE * METRIC_SCALE) - LINE, unitColor, true);
            }
            if (rightLarge) {
                drawScaled(graphics, font, rightValue, equalColumns ? rightX : x + width - scaled(font, rightValue), valueY, rightColor);
            } else {
                drawRight(graphics, font, rightValue, x + width, valueY + 1, rightColor);
            }
        }

        private static void drawScaled(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
            if (font.width(text) == 0) return;
            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(x, y, 0);
                pose.scale(METRIC_SCALE, METRIC_SCALE, 1.0F);
                graphics.drawString(font, text, 0, 0, color, true);
            } finally {
                pose.popPose();
            }
        }
    }

    /** One line of a panel: an optional glyph, a left text and an optional right-aligned value. */
    public record PanelLine(Glyph glyph, Component left, int leftColor, Component right, int rightColor) {}

    /** Inset box with an optional glyph title and status, followed by compact lines. */
    private record Panel(Glyph glyph, Component title, Component right, int rightColor, List<PanelLine> lines)
        implements Entry {
        private boolean titled() {
            return !title.getString().isEmpty();
        }

        private int inner() {
            return (titled() ? LINE + 3 : 0) + lines.size() * (LINE + 2) - 2;
        }

        public int width(Font font) {
            int widest = titled()
                ? (glyph == null ? 0 : ICON_SPACE) + font.width(title) + gap(font, right) + font.width(right) : 0;
            for (PanelLine line : lines) {
                widest = Math.max(widest, (line.glyph() == null ? 0 : ICON_SPACE) + font.width(line.left())
                    + gap(font, line.right()) + font.width(line.right()));
            }
            return widest + BANNER_PAD * 2;
        }

        public int height() {
            return inner() + BANNER_PAD * 2 + 3;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            graphics.fill(x, y, x + width, y + inner() + BANNER_PAD * 2, TILE_BACKGROUND);
            int left = x + BANNER_PAD, end = x + width - BANNER_PAD, textY = y + BANNER_PAD + 1;
            if (titled()) {
                int titleX = left;
                if (glyph != null) {
                    glyph.draw(graphics, left, textY, CraftingTooltipCard.WARN);
                    titleX += ICON_SPACE;
                }
                graphics.drawString(font, title, titleX, textY, CraftingTooltipCard.PRIMARY, true);
                drawRight(graphics, font, right, end, textY, rightColor);
                textY += LINE + 3;
            }
            for (PanelLine line : lines) {
                int lineX = left;
                if (line.glyph() != null) {
                    line.glyph().draw(graphics, left, textY, line.leftColor());
                    lineX += ICON_SPACE;
                }
                graphics.drawString(font, line.left(), lineX, textY, line.leftColor(), true);
                drawRight(graphics, font, line.right(), end, textY, line.rightColor());
                textY += LINE + 2;
            }
        }
    }

    /** One coloured range of a scale bar, ending at {@code end} (0..1). */
    public record Band(double end, int color) {}

    /**
     * Bar split into bands with small gaps and a tick label under every band boundary. The part of each band
     * below {@code value} is drawn at full colour, the remainder dimmed.
     */
    private record Scale(List<Band> bands, double value, List<Component> ticks) implements Entry {
        public int width(Font font) {
            int total = 0;
            for (Component tick : ticks) total += font.width(tick) + 6;
            return total;
        }

        public int height() {
            return 4 + 3 + LINE + 2;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            double fill = Mth.clamp(value, 0.0D, 1.0D);
            int filled = x + (int) Math.round(width * fill);
            double start = 0.0D;
            for (int i = 0; i < bands.size(); i++) {
                Band band = bands.get(i);
                int left = x + (int) Math.round(width * start) + (i == 0 ? 0 : 1);
                int right = x + (int) Math.round(width * Mth.clamp(band.end(), 0.0D, 1.0D)) - (i == bands.size() - 1 ? 0 : 1);
                if (right > left) {
                    graphics.fill(left, y, right, y + 4, dim(band.color()));
                    int lit = Math.min(right, filled);
                    if (lit > left) graphics.fill(left, y, lit, y + 4, band.color());
                }
                start = band.end();
            }
            if (fill > 0.0D) {
                int marker = Math.min(x + width - 1, filled);
                graphics.fill(marker, y - 1, marker + 1, y + 5, CraftingTooltipCard.PRIMARY);
            }
            int tickY = y + 4 + 3;
            for (int i = 0; i < ticks.size(); i++) {
                Component tick = ticks.get(i);
                double at = i == 0 || bands.isEmpty() ? 0.0D : bands.get(Math.min(i, bands.size()) - 1).end();
                int tickWidth = font.width(tick);
                int tickX = Mth.clamp(x + (int) Math.round(width * at) - tickWidth / 2, x, x + width - tickWidth);
                boolean reached = at > 0.0D && fill >= at;
                graphics.drawString(font, tick, tickX, tickY,
                    reached ? CraftingTooltipCard.PRIMARY : CraftingTooltipCard.MUTED, true);
            }
        }

        private static int dim(int color) {
            int r = (color >> 16 & 0xFF) * 2 / 5, g = (color >> 8 & 0xFF) * 2 / 5, b = (color & 0xFF) * 2 / 5;
            return 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    /** Three short lines in a bordered box: accent heading, primary value and muted caption. */
    public record Tile(Component heading, Component value, Component caption, boolean active) {}

    private record Tiles(List<Tile> tiles) implements Entry {
        public int width(Font font) {
            int widest = 0;
            for (Tile tile : tiles) {
                widest = Math.max(widest, Math.max(font.width(tile.heading()),
                    Math.max(font.width(tile.value()), font.width(tile.caption()))));
            }
            return tiles.size() * (widest + TILE_PAD * 2) + Math.max(0, tiles.size() - 1) * TILE_GAP;
        }

        public int height() {
            return 3 * (LINE + 2) + TILE_PAD * 2 + 3;
        }

        public void draw(GuiGraphics graphics, Font font, int x, int y, int width) {
            int count = tiles.size();
            if (count == 0) return;
            int tileWidth = (width - (count - 1) * TILE_GAP) / count;
            int bottom = y + height() - 3;
            for (int i = 0; i < count; i++) {
                Tile tile = tiles.get(i);
                int left = x + i * (tileWidth + TILE_GAP);
                int right = i == count - 1 ? x + width : left + tileWidth;
                graphics.fill(left, y, right, bottom, tile.active() ? CraftingTooltipCard.ACCENT : TILE_BORDER);
                graphics.fill(left + 1, y + 1, right - 1, bottom - 1, tile.active() ? TILE_ACTIVE : TILE_BACKGROUND);
                int textX = left + TILE_PAD, textY = y + TILE_PAD;
                graphics.drawString(font, tile.heading(), textX, textY, CraftingTooltipCard.ACCENT, true);
                graphics.drawString(font, tile.value(), textX, textY + LINE + 2, CraftingTooltipCard.PRIMARY, true);
                graphics.drawString(font, tile.caption(), textX, textY + 2 * (LINE + 2), CraftingTooltipCard.MUTED, true);
            }
        }
    }

    private final Font font;
    private final List<Entry> entries;
    private final int width;
    private final int[] heights;

    private CraftingListCard(List<Entry> entries, Font font, Component title, int minimumWidth) {
        this.font = font;
        this.entries = List.copyOf(entries);
        int content = Math.max(Math.max(MIN_WIDTH, minimumWidth), (int) Math.ceil(font.width(title) / CraftingTooltipCard.CONTENT_SCALE));
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
        private int minimumWidth = MIN_WIDTH;

        private Builder() {}

        public Builder header(Glyph glyph, Component title, Component right, int rightColor) {
            return header(glyph, title, right, rightColor, null);
        }

        public Builder header(Glyph glyph, Component title, Component right, int rightColor, Glyph rightGlyph) {
            entries.add(new Header(glyph, title.copy().withStyle(ChatFormatting.BOLD), right, rightColor, rightGlyph));
            return this;
        }

        public Builder divider() {
            return divider(DIVIDER);
        }

        public Builder divider(int color) {
            entries.add(new Divider(color));
            return this;
        }

        public Builder space(int height) {
            if (height > 0) entries.add(new Space(height));
            return this;
        }

        public Builder minimumWidth(int width) {
            minimumWidth = Math.max(MIN_WIDTH, width);
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

        /** A row whose leading icon is the pass/fail glyph, with a right-aligned value in the same colour. */
        public Builder checkRow(boolean ok, Component left, Component right) {
            int color = ok ? CraftingTooltipCard.OK : DANGER;
            entries.add(new Row(ok ? Glyph.CHECK : Glyph.CROSS, color, left, CraftingTooltipCard.PRIMARY, null,
                right, Component.empty(), color, false));
            return this;
        }

        /** A tinted callout box; {@code detail} may be empty. */
        public Builder banner(Glyph glyph, Component title, Component detail, int color, int background) {
            entries.add(new Banner(glyph, title, detail, color, background));
            return this;
        }

        /** A muted label over a large value, with a small right-aligned note on the value line. */
        public Builder metric(Component label, Component value, int valueColor, Component note, int noteColor) {
            entries.add(new Metric(label, value, valueColor, Component.empty(), 0, Component.empty(), note, noteColor,
                false, false));
            return this;
        }

        /** Two label-over-large-value columns, the second right-aligned. */
        public Builder metrics(Component label, Component value, int valueColor,
                               Component rightLabel, Component rightValue, int rightColor) {
            entries.add(new Metric(label, value, valueColor, Component.empty(), 0, rightLabel, rightValue, rightColor,
                true, false));
            return this;
        }

        /** Two equally wide metric columns with both labels and values aligned to each column's left edge. */
        public Builder metricColumns(Component label, Component value, int valueColor,
                                     Component rightLabel, Component rightValue, int rightColor) {
            entries.add(new Metric(label, value, valueColor, Component.empty(), 0, rightLabel, rightValue, rightColor,
                true, true));
            return this;
        }

        /** A muted label over a large value followed by a small unit, such as {@code 0.0 AE/t}. */
        public Builder metricUnit(Component label, Component value, int valueColor, Component unit, int unitColor) {
            entries.add(new Metric(label, value, valueColor, unit, unitColor, Component.empty(), Component.empty(), 0,
                false, false));
            return this;
        }

        /** An inset box; an empty {@code title} leaves only the lines. */
        public Builder panel(Glyph glyph, Component title, Component right, int rightColor, List<PanelLine> lines) {
            entries.add(new Panel(glyph, title, right, rightColor, List.copyOf(lines)));
            return this;
        }

        /** A banded bar; {@code ticks} holds one label for 0 and one per band end. */
        public Builder scale(List<Band> bands, double value, List<Component> ticks) {
            entries.add(new Scale(List.copyOf(bands), value, List.copyOf(ticks)));
            return this;
        }

        public Builder tiles(List<Tile> tiles) {
            entries.add(new Tiles(List.copyOf(tiles)));
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
            entries.add(new Text(null, left, right, color));
            return this;
        }

        /** Wrapped text behind a leading glyph, used for longer hints and notes. */
        public Builder wrapped(Glyph glyph, Component text, int color) {
            entries.add(new Text(glyph, text, Component.empty(), color));
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

        /** Every non-empty text of the card in display order, for narration and content checks. */
        public List<Component> texts() {
            List<Component> texts = new ArrayList<>();
            for (Entry entry : entries) {
                switch (entry) {
                    case Header header -> add(texts, header.title(), header.right());
                    case Section section -> add(texts, section.label(), section.right());
                    case Row row -> add(texts, row.left(), row.right(), row.suffix());
                    case Note note -> add(texts, note.text());
                    case Text text -> add(texts, text.left(), text.right());
                    case IconHeader header -> add(texts, header.title(), header.subtitle());
                    case Banner banner -> add(texts, banner.title(), banner.detail());
                    case Metric metric -> add(texts, metric.label(), metric.value(), metric.unit(), metric.rightLabel(),
                        metric.rightValue());
                    case Panel panel -> {
                        add(texts, panel.title(), panel.right());
                        panel.lines().forEach(line -> add(texts, line.left(), line.right()));
                    }
                    case Scale scale -> texts.addAll(scale.ticks());
                    case Tiles tiles -> tiles.tiles().forEach(tile -> add(texts, tile.heading(), tile.value(), tile.caption()));
                    default -> {}
                }
            }
            return texts;
        }

        private static void add(List<Component> texts, Component... values) {
            for (Component value : values) {
                if (!value.getString().isEmpty()) texts.add(value);
            }
        }

        /** A complete card for graph panels that provide their own frame and placement. */
        public ModularUITooltipComponent buildComponent() {
            return new ModularUITooltipComponent(new CraftingListCard(entries, Minecraft.getInstance().font,
                Component.empty(), minimumWidth));
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
            return List.of(title, new ModularUITooltipComponent(new CraftingListCard(body, font, title, minimumWidth)));
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
