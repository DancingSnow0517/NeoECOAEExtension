package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.IBindable;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.IDataSource;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import org.jetbrains.annotations.Nullable;

/** Percentage thermometer with the actual 20/50/80 percent operating thresholds. */
final class LargeWorkstationTemperatureGauge extends UIElement implements IBindable<Float> {
    private float value;

    @Override
    public IDataSource<Float> setValue(@Nullable Float value) {
        this.value = value == null || !Float.isFinite(value) ? 0 : JdkCompat.clamp(value, 0, 100);
        return this;
    }

    @Override
    public Float getValue() { return value; }

    @Override
    public void drawContents(GUIContext context) {
        int x = (int) getPositionX();
        int y = (int) getPositionY();
        context.graphics.fill(x, y, x + 92, y + 4, 0xFF403E53);
        int color = value >= 100 ? 0xFFE34D4D : value >= 80 ? 0xFFE79843
            : value >= 50 ? 0xFFE4C85A : value >= 20 ? 0xFF79B369 : 0xFF7E9FC5;
        context.graphics.fill(x + 1, y + 1, x + 1 + (int) (90 * value / 100), y + 3, color);
        for (int threshold : new int[] {20, 50, 80}) {
            int marker = x + 1 + 90 * threshold / 100;
            context.graphics.fill(marker, y, marker + 1, y + 4, 0xFFE8E6EE);
        }
    }
}
