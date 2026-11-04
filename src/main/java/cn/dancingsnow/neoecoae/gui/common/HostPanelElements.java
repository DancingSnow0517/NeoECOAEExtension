package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class HostPanelElements {
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
            return DisplayNumbers.grouped(micros) + " us";
        }
        return DisplayNumbers.grouped(BigDecimal.valueOf(safeNanos, 6).setScale(3, RoundingMode.HALF_UP)) + " ms";
    }

    public static String formatPerformanceValue(long averageNanos) {
        long safeNanos = Math.max(0L, averageNanos);
        long micros = Math.round(safeNanos / 1_000.0D);
        String millis = DisplayNumbers.grouped(BigDecimal.valueOf(safeNanos, 6).setScale(3, RoundingMode.HALF_UP));
        return HostText.expandedNumber(micros) + " us/" + millis + " ms";
    }
}
