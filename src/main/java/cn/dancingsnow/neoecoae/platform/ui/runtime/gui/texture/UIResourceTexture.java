package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.editor.resource.*;
import net.minecraft.client.gui.GuiGraphics;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
public record UIResourceTexture(BuiltinPath path) implements IGuiTexture {
    @Override public IGuiTexture getRawTexture(){var texture=TexturesResource.INSTANCE.getResource(path);return texture==null?IGuiTexture.EMPTY:texture.getRawTexture();}
    @Override public void draw(GuiGraphics graphics,float mx,float my,float x,float y,float w,float h,float tick){getRawTexture().draw(graphics,mx,my,x,y,w,h,tick);}
    @Override public void draw(GUIContext context,float x,float y,float w,float h){getRawTexture().draw(context,x,y,w,h);}
}
