package cn.dancingsnow.neoecoae.client.gui.ldlib.host;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the segmented side-button rails used by the 1.21.1 ECO host screens.
 */
public final class NEHostSideButtonRenderer {
    public static final int BAR_WIDTH = 23;
    public static final int LEFT_X = -21;
    public static final int RIGHT_X_OFFSET = -2;
    private static final int TOP_HEIGHT = 30;
    private static final int MIDDLE_HEIGHT = 24;
    private static final int BOTTOM_HEIGHT = 27;
    private static final int FIRST_STEP = 23;
    private static final int MIDDLE_STEP = 22;

    private NEHostSideButtonRenderer() {}

    public static int buttonY(int index) {
        return index == 0 ? 3 : 25 + (index - 1) * MIDDLE_STEP;
    }

    public static void drawLeft(GuiGraphics graphics, int originX, int originY, int count, int mouseX, int mouseY) {
        draw(graphics, originX + LEFT_X, originY, count, false, mouseX, mouseY);
    }

    public static void drawRight(
            GuiGraphics graphics, int originX, int originY, int contentWidth, int count, int mouseX, int mouseY) {
        draw(graphics, originX + contentWidth + RIGHT_X_OFFSET, originY + 1, count, true, mouseX, mouseY);
    }

    private static void draw(GuiGraphics graphics, int x, int y, int count, boolean mirrored, int mouseX, int mouseY) {
        for (int index = 0; index < count; index++) {
            int top = index == 0 ? 0 : FIRST_STEP + (index - 1) * MIDDLE_STEP;
            int height = index == 0 ? TOP_HEIGHT : index == count - 1 ? BOTTOM_HEIGHT : MIDDLE_HEIGHT;
            String part = index == 0 ? "up" : index == count - 1 ? "down" : "middle";
            String file = "eco_" + (mirrored ? "mirrored_" : "") + "button_slot_" + part + ".png";
            IGuiTexture texture = new ResourceTexture(NeoECOAE.id("textures/gui/" + file));
            texture.draw(graphics, mouseX, mouseY, x, y + top, BAR_WIDTH, height);
        }
    }
}
