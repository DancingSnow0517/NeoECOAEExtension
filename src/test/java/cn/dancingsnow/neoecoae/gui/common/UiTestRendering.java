package cn.dancingsnow.neoecoae.gui.common;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UIElementRendererRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;

/**
 * 26.1 port: LDLib2 26.1 made {@code UIElement#drawBackgroundAdditional} protected and moved the draw phases into
 * per-class {@code UIElementRenderer}s (see main's {@link ElementRenderHooks}). Tests draw an element's
 * background-additional phase through the renderer registry, exactly as LDLib2 does at runtime.
 */
public final class UiTestRendering {
    private UiTestRendering() {}

    public static java.util.List<net.minecraft.network.chat.Component> tooltipTexts(
            com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips tooltip) {
        return tooltip.tooltips().stream().filter(net.minecraft.network.chat.Component.class::isInstance)
            .map(net.minecraft.network.chat.Component.class::cast).toList();
    }

    public static Object tooltipComponent(com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips tooltip) {
        return tooltip.tooltips().stream().filter(net.minecraft.world.inventory.tooltip.TooltipComponent.class::isInstance)
            .findFirst().orElse(null);
    }

    public static void drawBackgroundAdditional(UIElement element, IGUIContext context) {
        UIElementRendererRegistry.findRenderer(element).drawBackgroundAdditional(element, context);
    }
}
