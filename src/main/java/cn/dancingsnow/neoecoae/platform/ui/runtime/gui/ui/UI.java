package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.style.StylesheetManager;
import java.util.List;
public record UI(UIElement root,List<StylesheetManager.Stylesheet> stylesheets){
    public static UI of(UIElement root){return new UI(root,List.of());}
    public static UI of(UIElement root,List<StylesheetManager.Stylesheet> sheets){return new UI(root,List.copyOf(sheets));}
}
