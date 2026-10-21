package cn.dancingsnow.neoecoae.client.gui.widget;
import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;
public final class NEButtonText {
    private NEButtonText(){}
    public static void renderButtonText(GuiGraphics graphics,Font font,Component text,int x1,int y1,int x2,int y2,int yOffset,int color){
        int width=font.width(text),available=Math.max(1,x2-x1);
        float scale=Math.min(1,(float)available/Math.max(1,width));
        graphics.pose().pushPose();graphics.pose().translate((x1+x2-width*scale)/2f,(y1+y2-font.lineHeight*scale)/2f-yOffset,0);
        graphics.pose().scale(scale,scale,1);graphics.drawString(font,text,0,0,color,false);graphics.pose().popPose();
    }
}
