package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.IBindable;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.utils.Size;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Editing stays local; the element's binding is the single C2S transport. */
public class TextField extends UIElement implements IBindable<String> {
    public static class TextFieldStyle {
        int color=-1;boolean shadow;IGuiTexture focus;Component placeholder=Component.empty();
        public TextFieldStyle textColor(int value){color=value;return this;}
        public TextFieldStyle textShadow(boolean value){shadow=value;return this;}
        public TextFieldStyle focusOverlay(IGuiTexture value){focus=value;return this;}
        public TextFieldStyle placeholder(Component value){placeholder=value;return this;}
    }
    private final TextFieldStyle textStyle=new TextFieldStyle();
    private Consumer<String> responder;
    private final TextFieldWidget field=new TextFieldWidget(0,0,60,12,null,null){
        @Override protected void onTextChanged(String text){String value=textValidator.apply(text);
            if(!java.util.Objects.equals(value,currentString)){setCurrentString(value);if(responder!=null)responder.accept(value);}}
    };
    public TextField(){field.setBordered(false);addWidget(field);layout(l->l.width(60).height(12));}
    public TextField setNumbersOnlyInt(int min,int max){field.setNumbersOnly(min,max);return this;}
    public TextField setTextResponder(Consumer<String> value){responder=value;return this;}
    public TextField textFieldStyle(Consumer<TextFieldStyle> action){action.accept(textStyle);field.setTextColor(textStyle.color);return this;}
    @Override public String getValue(){return field.getCurrentString();}
    @Override public TextField setValue(String value){field.setCurrentString(value==null?"":value);return this;}
    public boolean isFocused(){return field.isFocus();}
    @Override public void drawBackgroundAdditional(GUIContext context){if(isFocused()&&textStyle.focus!=null)context.drawTexture(textStyle.focus,getPositionX(),getPositionY(),getSizeWidth(),getSizeHeight());}
    @Override public void applyBounds(float x,float y,float w,float h){super.applyBounds(x,y,w,h);field.setSize(new Size(Math.round(w),Math.round(h)));}
}
