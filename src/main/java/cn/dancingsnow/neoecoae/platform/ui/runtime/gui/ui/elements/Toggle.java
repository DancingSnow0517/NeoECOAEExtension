package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.IBindable;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import java.util.function.Consumer;
public class Toggle extends UIElement implements IBindable<Boolean> {
    public final Button toggleButton=new Button().addClass("__toggle_button__");
    public static class ToggleStyle {
        IGuiTexture mark=IGuiTexture.EMPTY,unmark=IGuiTexture.EMPTY;
        public ToggleStyle markTexture(IGuiTexture value){mark=value;return this;}
        public ToggleStyle unmarkTexture(IGuiTexture value){unmark=value;return this;}
    }
    private boolean value;private Consumer<Boolean> changed;
    private final ToggleStyle toggleStyle=new ToggleStyle();
    private final UIElement mark=new UIElement().layout(l->l.widthPercent(100).heightPercent(100));
    public Toggle(){toggleButton.setOnClick(e->setValue(!value));toggleButton.addChild(mark);addChild(toggleButton);}
    public Toggle noText(){toggleButton.noText();return this;}
    public Toggle toggleStyle(Consumer<ToggleStyle> action){action.accept(toggleStyle);updateMark();return this;}
    public Toggle toggleButton(Consumer<Button> action){action.accept(toggleButton);return this;}
    public Toggle setOnToggleChanged(Consumer<Boolean> callback){changed=callback;return this;}
    @Override public Boolean getValue(){return value;}
    @Override public Toggle setValue(Boolean state){boolean next=Boolean.TRUE.equals(state);if(value!=next){value=next;updateMark();if(changed!=null)changed.accept(value);}return this;}
    private void updateMark(){mark.style(s->s.backgroundTexture(value?toggleStyle.mark:toggleStyle.unmark));}
}
