package cn.dancingsnow.neoecoae.gui.common;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class HostPanelElements {
    private static final ThreadLocal<DecimalFormat> PERFORMANCE_MS_FORMAT = ThreadLocal.withInitial(() ->
        new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US)));

    private HostPanelElements() {
    }

    public static UIElement hostCard(int width, int height) {
        return new UIElement()
            .addClass("eco-host-card")
            .layout(layout -> layout.width(width).height(height));
    }

    public static void inventoryTitleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
            .adaptiveWidth(true)
            .textWrap(TextWrap.HOVER_ROLL)
            .textColor(0x3f3d52)
            .textShadow(false);
    }

    public static String formatPerformanceCornerValue(long averageNanos) {
        long safeNanos = Math.max(0L, averageNanos);
        long micros = Math.round(safeNanos / 1_000.0D);
        if (micros < 1_000L) {
            return micros + " us";
        }
        return PERFORMANCE_MS_FORMAT.get().format(safeNanos / 1_000_000.0D) + " ms";
    }

    public static String formatPerformanceValue(long averageNanos) {
        long safeNanos = Math.max(0L, averageNanos);
        long micros = Math.round(safeNanos / 1_000.0D);
        String millis = PERFORMANCE_MS_FORMAT.get().format(safeNanos / 1_000_000.0D);
        return micros + " us/" + millis + " ms";
    }
}
