package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import net.minecraft.client.gui.GuiGraphics;

public interface IGuiTexture {
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    void draw(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width, float height, float partialTicks);
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    default void draw(GUIContext context, float x, float y, float width, float height) {
        draw(context.graphics, context.mouseX, context.mouseY, x, y, width, height, context.partialTick);
    }
    default IGuiTexture getRawTexture() { return this; }
    default IGuiTexture copy() { return this; }
    IGuiTexture EMPTY = new EmptyTexture();
    static IGuiTexture group(IGuiTexture... textures) {
        return new IGuiTexture() {
            @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
            @Override public void draw(GuiGraphics graphics,float mouseX,float mouseY,float x,float y,float width,float height,float partialTicks){
                for(var texture:textures)if(texture!=null)texture.draw(graphics,mouseX,mouseY,x,y,width,height,partialTicks);
            }
            @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
            @Override public void draw(GUIContext context,float x,float y,float width,float height){
                for(var texture:textures)if(texture!=null)texture.draw(context,x,y,width,height);
            }
        };
    }
}
