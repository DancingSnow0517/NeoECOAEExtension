package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event;

import java.util.List;
import net.minecraft.network.chat.Component;
public record HoverTooltips(List<Component> tooltips, Object image, Object positioner, Object style) {
    public static HoverTooltips empty(){return new HoverTooltips(List.of(),null,null,null);}
    public HoverTooltips append(Component... lines){return append(List.of(lines));}
    public HoverTooltips append(List<Component> lines){var all=new java.util.ArrayList<>(tooltips);all.addAll(lines);return new HoverTooltips(List.copyOf(all),image,positioner,style);}
    public List<List<Component>> tooltipTexts(){return List.of(tooltips);}
}
