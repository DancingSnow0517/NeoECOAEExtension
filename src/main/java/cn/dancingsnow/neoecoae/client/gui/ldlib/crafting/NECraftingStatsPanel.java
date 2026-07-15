package cn.dancingsnow.neoecoae.client.gui.ldlib.crafting;

import static cn.dancingsnow.neoecoae.gui.ldlib.crafting.NECraftingLayout.*;

import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostTextures;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Draws recipe capacity/performance statistics and owns the complete statistics tooltip. */
public final class NECraftingStatsPanel {
    private static final int OVERFLOW_COLOR = 0xFF000000;
    private static final ThreadLocal<DecimalFormat> PERFORMANCE_FORMAT =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US)));

    public void drawBackground(NECraftingRenderContext context, NECraftingUiState state, int mouseX, int mouseY) {
        NEHostTextures.drawPanel(
                context.graphics(),
                context.x(STATS_AREA_X),
                context.y(STATS_AREA_Y),
                STATS_AREA_W,
                STATS_AREA_H,
                mouseX,
                mouseY);
        drawUsageBar(context, state.occupiedRecipeSlots(), state.maxRecipeSlots());
    }

    public void draw(NECraftingRenderContext context, NECraftingUiState state) {
        int x = STATS_AREA_X + 8;
        int rightX = STATS_AREA_X + STATS_AREA_W - 8;
        context.draw(
                Component.translatable("gui.neoecoae.crafting.stats"),
                x,
                STATS_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        context.drawRight(
                Component.literal(formatPerformanceCorner(state.performanceAverageNanos())),
                rightX,
                STATS_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_VALUE);
        drawPair(context, state, x, STATS_AREA_Y + 19, rightX - x);
        drawValue(
                context,
                Component.translatable("gui.neoecoae.crafting.batch_parallel").getString() + ": ",
                state.batchParallel(),
                x,
                STATS_AREA_Y + 44);
        drawOverflow(context, state, x, STATS_AREA_Y + 55);
    }

    public boolean drawTooltip(NECraftingRenderContext context, NECraftingUiState state, int mouseX, int mouseY) {
        if (!contains(context, mouseX, mouseY)) {
            return false;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.neoecoae.crafting.parallel_core_tiers"));
        lines.add(Component.literal("FT4: " + countTier(state, 1) + " x " + parallelPerCore(1, state.overclocked())));
        lines.add(Component.literal("FT6: " + countTier(state, 2) + " x " + parallelPerCore(2, state.overclocked())));
        lines.add(Component.literal("FT9: " + countTier(state, 3) + " x " + parallelPerCore(3, state.overclocked())));
        lines.add(Component.translatable("gui.neoecoae.crafting.recipe_slots")
                .append(": ")
                .append(Component.literal(NELDLibText.usedTotal(state.occupiedRecipeSlots(), state.maxRecipeSlots()))));
        lines.add(Component.translatable("gui.neoecoae.crafting.batch_parallel")
                .append(": ")
                .append(Component.literal(NELDLibText.number(state.batchParallel()))));
        lines.add(Component.literal(formatPerformanceLine(state.performanceAverageNanos())));
        context.graphics().renderTooltip(context.font(), lines, Optional.empty(), mouseX, mouseY);
        return true;
    }

    private void drawUsageBar(NECraftingRenderContext context, long current, long max) {
        GuiGraphics graphics = context.graphics();
        int x = context.x(STATS_AREA_X + 8);
        int y = context.y(STATS_AREA_Y + 31);
        int width = STATS_AREA_W - 16;
        int height = 9;
        NEHostTextures.drawPanel(graphics, x, y, width, height, 0, 0);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF201E27);
        int innerWidth = Math.max(0, width - 6);
        int innerHeight = Math.max(0, height - 6);
        if (innerWidth <= 0 || innerHeight <= 0) {
            return;
        }
        int fillWidth = ratioWidth(current, max, innerWidth);
        graphics.fill(x + 3, y + 3, x + 3 + innerWidth, y + 3 + innerHeight, 0xFF201E27);
        if (fillWidth > 0) {
            graphics.fill(x + 3, y + 3, x + 3 + fillWidth, y + 3 + innerHeight, NELDLibStyle.DARK_TEXT_VALUE);
        }
    }

    private void drawPair(NECraftingRenderContext context, NECraftingUiState state, int x, int y, int maxWidth) {
        String label =
                Component.translatable("gui.neoecoae.crafting.recipe_slots").getString() + ": ";
        String current = NELDLibText.number(state.occupiedRecipeSlots());
        String max = NELDLibText.number(state.maxRecipeSlots());
        int rawWidth = context.font().width(label)
                + context.font().width(current)
                + context.font().width(" / ")
                + context.font().width(max);
        float scale = Math.min(NECraftingRenderContext.TEXT_SCALE, maxWidth / (float) Math.max(1, rawWidth));
        GuiGraphics graphics = context.graphics();
        graphics.pose().pushPose();
        graphics.pose().translate(context.x(x), context.y(y), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        int cursor = 0;
        graphics.drawString(context.font(), label, cursor, 0, NELDLibStyle.DARK_TEXT_MUTED, false);
        cursor += context.font().width(label);
        graphics.drawString(context.font(), current, cursor, 0, NELDLibStyle.DARK_TEXT_SUCCESS, false);
        cursor += context.font().width(current);
        graphics.drawString(context.font(), " / ", cursor, 0, NELDLibStyle.DARK_TEXT_MUTED, false);
        cursor += context.font().width(" / ");
        graphics.drawString(context.font(), max, cursor, 0, NELDLibStyle.DARK_TEXT_VALUE, false);
        graphics.pose().popPose();
    }

    private void drawValue(NECraftingRenderContext context, String label, long value, int x, int y) {
        int cursor = context.draw(label, x, y, NELDLibStyle.DARK_TEXT_MUTED);
        context.drawAbsolute(
                NELDLibText.number(value), context.x(x) + cursor, context.y(y), NELDLibStyle.DARK_TEXT_VALUE);
    }

    private void drawOverflow(NECraftingRenderContext context, NECraftingUiState state, int x, int y) {
        String label =
                Component.translatable("gui.neoecoae.host.crafting.overflow").getString() + ": ";
        int cursor = context.draw(label, x, y, NELDLibStyle.DARK_TEXT_MUTED);
        context.drawAbsolute(
                NELDLibText.number(state.overflowThreads()), context.x(x) + cursor, context.y(y), OVERFLOW_COLOR);
    }

    private boolean contains(NECraftingRenderContext context, int mouseX, int mouseY) {
        int x = context.x(STATS_AREA_X);
        int y = context.y(STATS_AREA_Y);
        return mouseX >= x && mouseX < x + STATS_AREA_W && mouseY >= y && mouseY < y + STATS_AREA_H;
    }

    private static int ratioWidth(long current, long max, int width) {
        return max <= 0 ? 0 : (int) Math.round(Math.min(1.0D, Math.max(0.0D, current / (double) max)) * width);
    }

    private static int countTier(NECraftingUiState state, int tier) {
        return (int) state.parallelCoreTiers().stream()
                .filter(value -> value == tier)
                .count();
    }

    private static int parallelPerCore(int tier, boolean overclocked) {
        return switch (tier) {
            case 3 -> overclocked ? 384 : 256;
            case 2 -> overclocked ? 96 : 72;
            default -> overclocked ? 32 : 24;
        };
    }

    private static String formatPerformanceCorner(long nanos) {
        long safe = Math.max(0L, nanos);
        long micros = Math.round(safe / 1_000.0D);
        return micros < 1_000L ? micros + " μs" : PERFORMANCE_FORMAT.get().format(safe / 1_000_000.0D) + " ms";
    }

    private static String formatPerformanceLine(long nanos) {
        long safe = Math.max(0L, nanos);
        return Component.translatable("gui.neoecoae.crafting.performance").getString()
                + ":"
                + Math.round(safe / 1_000.0D)
                + " μs/"
                + PERFORMANCE_FORMAT.get().format(safe / 1_000_000.0D)
                + " ms";
    }
}
