package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public class Button extends UIElement{
    public final TextElement text=new TextElement().addClass("__button_text__");
    public static class ButtonStyle{
        public IGuiTexture base,hover,pressed;
        public boolean inlineBase,inlineHover,inlinePressed;
        public ButtonStyle baseTexture(IGuiTexture v){base=v;inlineBase=true;return this;}
        public ButtonStyle hoverTexture(IGuiTexture v){hover=v;inlineHover=true;return this;}
        public ButtonStyle pressedTexture(IGuiTexture v){pressed=v;inlinePressed=true;return this;}
        public IGuiTexture baseTexture(){return base;}
        public IGuiTexture hoverTexture(){return hover;}
        public IGuiTexture pressedTexture(){return pressed;}
    }
    private final ButtonStyle buttonStyle=new ButtonStyle();
    private Consumer<UIEvent> click,serverClick;
    private boolean pressed;
    public enum State { DEFAULT, HOVERED, PRESSED }
    public State getState(){return pressed?State.PRESSED:isSelfOrChildHover()?State.HOVERED:State.DEFAULT;}
    public Button(){layout(l->l.height(16));addChild(text);}
    @Override public String getElementName(){return "button";}
    public Button setText(Component value){text.setText(value);return this;}
    public Button setText(String value){text.setText(value);return this;}
    public Button setText(String value,boolean translate){text.setText(value,translate);return this;}
    public Button noText(){text.setDisplay(false);return this;}
    public Button setOnClick(Consumer<UIEvent> callback){click=callback;return this;}
    public Button setOnServerClick(Consumer<UIEvent> callback){serverClick=callback;return this;}
    public Button buttonStyle(Consumer<ButtonStyle> config){config.accept(buttonStyle);return this;}
    public Button addPreIcon(IGuiTexture texture){var icon=new UIElement().style(s->s.backgroundTexture(texture)).layout(l->l.width(16).height(16));addChild(0,icon);return this;}
    public Button addPostIcon(IGuiTexture texture){addChild(new UIElement().style(s->s.backgroundTexture(texture)).layout(l->l.width(16).height(16)));return this;}
    public Button textStyle(Consumer<TextElement.TextStyle> config){text.textStyle(config);return this;}
    @Override protected void onMouseDown(UIEvent event){if(event.button==0){pressed=true;event.hasHandler=true;event.stopPropagation();}}
    @Override protected void onMouseUp(UIEvent event){if(pressed&&event.button==0){pressed=false;event.hasHandler=true;
        if(!isMouseOverElement(event.x,event.y))return;if(click!=null)click.accept(event);if(serverClick!=null)writeClientAction(710,b->b.writeVarInt(event.button));}}
    @Override public void handleClientAction(int id,FriendlyByteBuf buffer){
        if(id!=710){super.handleClientAction(id,buffer);return;}if(!activeMenu()||serverClick==null||!isActive())return;
        int button=buffer.readVarInt();if(button!=0)return;serverClick.accept(new UIEvent(UIEvents.CLICK,this,0,0,button));
    }
    @Override public void drawBackgroundAdditional(GUIContext context){
        IGuiTexture texture=pressed&&buttonStyle.pressed!=null?buttonStyle.pressed:isSelfOrChildHover()&&buttonStyle.hover!=null?buttonStyle.hover:buttonStyle.base;
        if(texture!=null)context.drawTexture(texture,getPositionX(),getPositionY(),getSizeWidth(),getSizeHeight());
    }
    public ButtonStyle getButtonStyle(){return buttonStyle;}
}
