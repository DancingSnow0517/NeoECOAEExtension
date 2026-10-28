package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture;
import net.minecraft.client.gui.GuiGraphics;
public class ColorBorderTexture extends ColorRectTexture {
    private final float border;
    public ColorBorderTexture(float border,int color){super(color);this.border=border;}
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    @Override public void draw(GuiGraphics g,float mx,float my,float x,float y,float w,float h,float tick){
        g.fill(Math.round(x),Math.round(y),Math.round(x+w),Math.round(y+border),color);
        g.fill(Math.round(x),Math.round(y+h-border),Math.round(x+w),Math.round(y+h),color);
        g.fill(Math.round(x),Math.round(y+border),Math.round(x+border),Math.round(y+h-border),color);
        g.fill(Math.round(x+w-border),Math.round(y+border),Math.round(x+w),Math.round(y+h-border),color);
    }
}
