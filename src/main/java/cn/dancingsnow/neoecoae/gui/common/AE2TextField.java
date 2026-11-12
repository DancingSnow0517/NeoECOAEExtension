package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

/** Text field drawn with AE2's bundled normal, focused and disabled field sprites. */
public class AE2TextField extends TextField {
    @Override
    public void drawBackgroundTexture(com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext ctx) {
        if (!(ctx instanceof GUIContext guiContext)) return;
        IGuiTexture texture = !isActive()
            ? NETextures.PRIORITY_TEXT_FIELD_DISABLED
            : isFocused() ? NETextures.PRIORITY_TEXT_FIELD_FOCUS : NETextures.PRIORITY_TEXT_FIELD;
        guiContext.drawTexture(texture, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }
}
