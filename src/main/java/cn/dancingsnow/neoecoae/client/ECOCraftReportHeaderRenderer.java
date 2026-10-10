package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Two independently fitted rows inside the report texture's 102 by 15 pixel text area. */
final class ECOCraftReportHeaderRenderer {
    private static final String KEY_PREFIX = "gui.neoecoae.crafting_report.";
    private static final int TEXT_COLOR = 0x403E53;
    private static final int LEFT = 53;
    private static final int TOP = 5;
    private static final int RIGHT = 155;
    private static final int BOTTOM = 20;
    private static final int ROW_WIDTH = 100;
    private static final int ROW_HEIGHT = 6;
    private static final float DEFAULT_SCALE = 2.0F / 3.0F;
    private static final MathContext TIME_PRECISION = new MathContext(5, RoundingMode.HALF_UP);

    private ECOCraftReportHeaderRenderer() {}

    record Summary(Component elapsed, Component bytes, List<Component> tooltip) {}

    static Summary pending(Component status) {
        Component bytes = text("bytes_pending");
        return new Summary(status, bytes, List.of(status, bytes));
    }

    static Summary completed(long nanos, @Nullable BigInteger usedBytes, ToIntFunction<Component> width) {
        Component elapsed = text("elapsed", formatMillis(nanos));
        Component bytes = usedBytes == null ? text("bytes_unknown") : fitBytes(usedBytes, width);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(text("elapsed", DisplayNumbers.grouped(BigDecimal.valueOf(Math.max(0L, nanos), 6)
            .toPlainString())));
        if (usedBytes == null) {
            tooltip.add(bytes);
        } else {
            // Keep the exact BigInteger here, including values beyond the compact formatter's units.
            String exact = DisplayNumbers.grouped(usedBytes.toString());
            if (exact.length() <= 32) {
                tooltip.add(text("bytes_only", exact + " B"));
            } else {
                tooltip.add(text("bytes_only", ""));
                for (int start = 0; start < exact.length(); start += 32) {
                    int end = Math.min(exact.length(), start + 32);
                    tooltip.add(Component.literal(exact.substring(start, end)
                        + (end == exact.length() ? " B" : "")));
                }
            }
        }
        return new Summary(elapsed, bytes, List.copyOf(tooltip));
    }

    private static Component fitBytes(BigInteger value, ToIntFunction<Component> width) {
        String compact = HostText.ae2Amount(value);
        int maxWidth = (int) (ROW_WIDTH / DEFAULT_SCALE);
        if (width.applyAsInt(text("bytes_only", byteUnit(compact))) > maxWidth) {
            compact = HostText.fitHugeAmount(value, maxWidth,
                amount -> width.applyAsInt(text("bytes_only", byteUnit(amount))));
        }
        return text("bytes_only", byteUnit(compact));
    }

    private static String byteUnit(String amount) {
        return amount.contains("e") || amount.contains("×10^")
            ? amount + " B" : DisplayNumbers.bytes(amount);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(KEY_PREFIX + key, args);
    }

    static String formatMillis(long nanos) {
        if (nanos <= 0) return "0.0000";
        BigDecimal millis = BigDecimal.valueOf(nanos, 6).round(TIME_PRECISION);
        int integerDigits = millis.precision() - millis.scale();
        int displayScale = Math.max(0, TIME_PRECISION.getPrecision() - integerDigits);
        return DisplayNumbers.grouped(millis.setScale(displayScale, RoundingMode.HALF_UP).toPlainString());
    }

    static void render(GuiGraphics graphics, Font font, int offsetX, int offsetY, Summary summary,
            @Nullable Long progressMillis) {
        // GuiGraphics scissors use window GUI coordinates, independent of the translated pose.
        graphics.enableScissor(offsetX + LEFT, offsetY + TOP, offsetX + RIGHT, offsetY + BOTTOM);
        try {
            drawRow(graphics, font, offsetX, offsetY, 6, summary.elapsed());
            drawRow(graphics, font, offsetX, offsetY, 13, summary.bytes());
            if (progressMillis != null) {
                int fill = 1 + (int) (progressMillis % 2_000L) * (ROW_WIDTH - 1) / 1_999;
                graphics.fill(54, 19, 154, 20, 0xFF6A6A72);
                graphics.fill(54, 19, 54 + fill, 20, 0xFF3D9B62);
            }
        } finally {
            graphics.flush();
            graphics.disableScissor();
        }
    }

    private static void drawRow(GuiGraphics graphics, Font font, int offsetX, int offsetY, int y,
            Component text) {
        float scale = Math.min(DEFAULT_SCALE, (float) ROW_HEIGHT / Math.max(1, font.lineHeight));
        scale = Math.min(scale, (float) ROW_WIDTH / Math.max(1, font.width(text)));
        graphics.enableScissor(offsetX + 54, offsetY + y, offsetX + 154, offsetY + y + ROW_HEIGHT);
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(54, y, 0);
            pose.scale(scale, scale, 1);
            graphics.drawString(font, text, 0, 0, TEXT_COLOR, false);
        } finally {
            graphics.flush();
            pose.popPose();
            graphics.disableScissor();
        }
    }

    static boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= LEFT && mouseX < RIGHT && mouseY >= TOP && mouseY < BOTTOM;
    }
}
