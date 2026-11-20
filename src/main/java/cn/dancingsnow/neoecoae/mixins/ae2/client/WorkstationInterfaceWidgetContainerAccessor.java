package cn.dancingsnow.neoecoae.mixins.ae2.client;

import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WidgetContainer.class, remap = false)
public interface WorkstationInterfaceWidgetContainerAccessor {
    @Accessor("compositeWidgets")
    Map<String, ICompositeWidget> neoecoae$compositeWidgets();
}
