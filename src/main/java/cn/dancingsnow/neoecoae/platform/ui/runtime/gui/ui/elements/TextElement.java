package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.IBindable;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class TextElement extends UIElement implements IBindable<Component>{
    public static class TextStyle{
        public boolean adaptiveWidth=true,adaptiveHeight=true,shadow;
        public boolean inlineColor,inlineShadow;
        public float size=9,lineSpacing;public int color=0xff404040;public Horizontal horizontal=Horizontal.LEFT;public Vertical vertical=Vertical.TOP;public TextWrap wrap=TextWrap.NONE;
        public float fontSize(){return size;}
        public int textColor(){return color;}
        public boolean textShadow(){return shadow;}
        public TextStyle lineSpacing(float value){lineSpacing=value;return this;}
        public TextStyle adaptiveHeight(boolean v){adaptiveHeight=v;return this;}
        public TextStyle adaptiveWidth(boolean v){adaptiveWidth=v;return this;}
        public TextStyle textWrap(TextWrap v){wrap=v;return this;}
        public TextStyle textColor(int v){color=(v&0xff000000)==0?0xff000000|v:v;inlineColor=true;return this;}
        public TextStyle textShadow(boolean v){shadow=v;inlineShadow=true;return this;}
        public TextStyle fontSize(float v){size=v;return this;}
        public TextStyle textAlignHorizontal(Horizontal v){horizontal=v;return this;}
        public TextStyle textAlignVertical(Vertical v){vertical=v;return this;}
    }
    private Component text=Component.empty();private final TextStyle textStyle=new TextStyle();
    @Override public Component getValue(){return text;}
    @Override public TextElement setValue(Component value){return setText(value);}
    @SuppressWarnings("unchecked") public <T extends TextElement>T setText(Component value){Component next=value==null?Component.empty():value;if(!text.equals(next)){text=next;markLayoutDirty();}return (T)this;}
    public <T extends TextElement>T setText(String value){return setText(Component.literal(value));}
    public <T extends TextElement>T setText(String value,boolean translate){return setText(translate?Component.translatable(value):Component.literal(value));}
    @SuppressWarnings("unchecked") public <T extends TextElement>T textStyle(Consumer<TextStyle> config){config.accept(textStyle);markLayoutDirty();return (T)this;}
    public TextStyle getTextStyle(){return textStyle;}
    public net.minecraft.client.gui.Font getFont(){return Minecraft.getInstance().font;}
    @Override public float intrinsicWidth(){return textStyle.adaptiveWidth?text.getString().codePointCount(0,text.getString().length())*6f*textStyle.size/9:0;}
    @Override public float intrinsicHeight(){return textStyle.adaptiveHeight?textStyle.size:0;}
    @Override public void drawContents(GUIContext context){
        var font=Minecraft.getInstance().font;float scale=textStyle.size/9,available=getSizeWidth()/scale;
        float textWidth=font.width(text),x=getPositionX(),y=getPositionY();
        int lines=textStyle.wrap==TextWrap.WRAP?font.split(text,Math.max(1,(int)available)).size():1;
        float textHeight=(font.lineHeight*lines+textStyle.lineSpacing*Math.max(0,lines-1))*scale;
        if(textStyle.vertical==Vertical.CENTER)y+=(getSizeHeight()-textHeight)/2;
        else if(textStyle.vertical==Vertical.BOTTOM)y+=getSizeHeight()-textHeight;
        if(textStyle.horizontal==Horizontal.CENTER)x+=(getSizeWidth()-textWidth*scale)/2;
        else if(textStyle.horizontal==Horizontal.RIGHT)x+=getSizeWidth()-textWidth*scale;
        if(textStyle.wrap==TextWrap.HOVER_ROLL&&isSelfOrChildHover()&&textWidth>available){
            long millis=net.minecraft.Util.getMillis();float delta=textWidth-available;
            x-=((Math.sin(millis/1000.0)*.5+.5)*delta)*scale;
        }
        context.graphics.pose().pushPose();context.graphics.pose().scale(scale,scale,1);
        if(textStyle.wrap==TextWrap.WRAP){float ly=y/scale;for(var line:font.split(text,Math.max(1,(int)available))){context.graphics.drawString(font,line,x/scale,ly,textStyle.color,textStyle.shadow);ly+=font.lineHeight+textStyle.lineSpacing;}}
        else context.graphics.drawString(font,text.getVisualOrderText(),x/scale,y/scale,textStyle.color,textStyle.shadow);
        context.graphics.pose().popPose();
    }
}
