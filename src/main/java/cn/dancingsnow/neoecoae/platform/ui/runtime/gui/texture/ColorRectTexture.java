package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture;
import net.minecraft.client.gui.GuiGraphics;
public class ColorRectTexture implements IGuiTexture {
    protected int color;
    public ColorRectTexture(int color){this.color=color;}
    public ColorRectTexture setColor(int value){color=value;return this;}
    @Override public void draw(GuiGraphics g,float mx,float my,float x,float y,float w,float h,float tick){g.fill(Math.round(x),Math.round(y),Math.round(x+w),Math.round(y+h),color);}
}
