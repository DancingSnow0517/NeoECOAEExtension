package cn.dancingsnow.neoecoae.gui.widget;

import appeng.core.AppEng;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.resources.ResourceLocation;

/**
 * The AE2-style window background, drawn by tiling the vanilla AE2 background texture into a
 * nine-patch. Unlike the old immediate-mode host canvas this is a normal {@link UIElement}: its
 * position and size come from the Taffy layout, and the background is painted in
 * {@link #drawBackgroundTexture} so it sits behind every child.
 */
public class ECOAePanel extends UIElement {
    private static final ResourceLocation BACKGROUND_TEXTURE = AppEng.makeId("textures/guis/background.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int CORNER = 4;
    private static final int TILE = 248;

    public ECOAePanel() {
        addClass("eco-ae-panel");
    }

    @Override
    public void drawBackgroundTexture(GUIContext guiContext) {
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        int w = Math.round(getSizeWidth());
        int h = Math.round(getSizeHeight());
        if (w < CORNER * 2 || h < CORNER * 2) {
            return;
        }
        int right = x + w;
        int bottom = y + h;

        blit(guiContext, x, y, 0, 0, CORNER, CORNER);
        blit(guiContext, right - CORNER, y, TEXTURE_SIZE - CORNER, 0, CORNER, CORNER);
        blit(guiContext, x, bottom - CORNER, 0, TEXTURE_SIZE - CORNER, CORNER, CORNER);
        blit(guiContext, right - CORNER, bottom - CORNER, TEXTURE_SIZE - CORNER, TEXTURE_SIZE - CORNER, CORNER, CORNER);

        for (int dx = 0; dx < w - CORNER * 2; dx += TILE) {
            int stripW = Math.min(TILE, w - CORNER * 2 - dx);
            blit(guiContext, x + CORNER + dx, y, CORNER, 0, stripW, CORNER);
            blit(guiContext, x + CORNER + dx, bottom - CORNER, CORNER, TEXTURE_SIZE - CORNER, stripW, CORNER);
            for (int dy = 0; dy < h - CORNER * 2; dy += TILE) {
                int stripH = Math.min(TILE, h - CORNER * 2 - dy);
                blit(guiContext, x + CORNER + dx, y + CORNER + dy, CORNER, CORNER, stripW, stripH);
            }
        }
        for (int dy = 0; dy < h - CORNER * 2; dy += TILE) {
            int stripH = Math.min(TILE, h - CORNER * 2 - dy);
            blit(guiContext, x, y + CORNER + dy, 0, CORNER, CORNER, stripH);
            blit(guiContext, right - CORNER, y + CORNER + dy, TEXTURE_SIZE - CORNER, CORNER, CORNER, stripH);
        }
    }

    private static void blit(GUIContext guiContext, int x, int y, int u, int v, int w, int h) {
        guiContext.graphics.blit(BACKGROUND_TEXTURE, x, y, w, h, u, v, w, h, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
