package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;

public class ElementStyle {
    public IGuiTexture background, overlay;
    public boolean inlineBackground;
    public int z;
    public boolean overflowVisible=true;
    public ElementStyle overflowVisible(boolean value){overflowVisible=value;return this;}
    public ElementStyle backgroundTexture(IGuiTexture texture) { background = texture;inlineBackground=true; return this; }
    public IGuiTexture backgroundTexture() { return background; }
    public ElementStyle overlayTexture(IGuiTexture texture) { overlay = texture; return this; }
    public ElementStyle zIndex(int value) { z = value; return this; }
}
