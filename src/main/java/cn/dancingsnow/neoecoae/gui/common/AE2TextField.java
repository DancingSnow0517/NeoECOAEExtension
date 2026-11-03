package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextField;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;

/** Text field drawn with AE2's bundled normal, focused and disabled field sprites. */
public class AE2TextField extends TextField {
    @Override
    public void drawBackgroundTexture(GUIContext guiContext) {
        IGuiTexture texture = !isActive()
            ? NETextures.PRIORITY_TEXT_FIELD_DISABLED
            : isFocused() ? NETextures.PRIORITY_TEXT_FIELD_FOCUS : NETextures.PRIORITY_TEXT_FIELD;
        guiContext.drawTexture(texture, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }
}
