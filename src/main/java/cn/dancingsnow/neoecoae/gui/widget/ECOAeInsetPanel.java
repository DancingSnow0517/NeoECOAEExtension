package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

/**
 * A recessed sub-panel: the layered bevel that the old host canvas drew with {@code drawInsetRect}.
 * Now a regular {@link UIElement} container — children flow inside it via flex layout, and the bevel
 * is painted in {@link #drawBackgroundTexture} from the element's own geometry instead of hardcoded
 * coordinates.
 */
public class ECOAeInsetPanel extends UIElement {
    private static final int EDGE = 0xFFC9C3D6;
    private static final int LAYER_1 = 0xFF0D0D11;
    private static final int INNER = 0xFF665F6D;
    private static final int OUTER = 0xFF17141E;
    private static final int MIDDLE = 0xFF2B2834;
    private static final int FACE = 0xFF605A66;

    public ECOAeInsetPanel() {
        addClass("eco-ae-inset");
    }

    @Override
    public void drawBackgroundTexture(GUIContext guiContext) {
        float x = getPositionX();
        float y = getPositionY();
        float w = getSizeWidth();
        float h = getSizeHeight();
        fill(guiContext, x, y, w, h, EDGE);
        fill(guiContext, x + 1, y + 1, w - 2, h - 2, LAYER_1);
        fill(guiContext, x + 2, y + 2, w - 4, h - 4, INNER);
        fill(guiContext, x + 3, y + 3, w - 6, h - 6, OUTER);
        fill(guiContext, x + 4, y + 4, w - 8, h - 8, MIDDLE);
        fill(guiContext, x + 5, y + 5, w - 10, h - 10, FACE);
    }

    private static void fill(GUIContext guiContext, float x, float y, float w, float h, int color) {
        guiContext.graphics.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }
}
