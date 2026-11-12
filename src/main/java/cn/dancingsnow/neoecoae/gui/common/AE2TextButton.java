package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/** AE2's vanilla-style text button: bundled button sprites, label colors and pressed offset. */
public class AE2TextButton extends Button implements ElementRenderHooks.Background, ElementRenderHooks.Contents {
    static { ElementRenderHooks.register(AE2TextButton.class); }
    private final String label;

    public AE2TextButton(String label) {
        this.label = label;
        noText();
    }

    @Override
    public void drawBackgroundAdditional(com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext ctx, Runnable drawParent) {
        if (!(ctx instanceof GUIContext guiContext)) return;
        IGuiTexture texture;
        if (!isActive()) {
            texture = NETextures.AE2_BUTTON_DISABLED;
        } else {
            texture = getState() == State.DEFAULT
                ? NETextures.AE2_BUTTON
                : NETextures.AE2_BUTTON_HIGHLIGHTED;
        }
        guiContext.drawTexture(texture, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }

    @Override
    public void drawContents(com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext ctx, Runnable drawDefault) {
        if (!(ctx instanceof GUIContext guiContext)) return;
        drawDefault.run();

        Font font = Minecraft.getInstance().font;
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        int width = Math.round(getSizeWidth());
        int height = Math.round(getSizeHeight());
        int textWidth = font.width(label);
        int textX = x + width / 2 - textWidth / 2;
        int textY = y + (height - 9) / 2 + 1;
        int yOffset;
        int color;
        if (!isActive()) {
            yOffset = -1;
            color = 0xFF413F54;
        } else if (getState() == State.DEFAULT) {
            yOffset = 1;
            color = 0xFFF2F2F2;
        } else {
            yOffset = 0;
            color = 0xFF517497;
        }
        cn.dancingsnow.neoecoae.gui.common.GuiDraw.text(guiContext.graphics, font, label, textX, textY - yOffset, color, false);
    }
}
