package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@LDLRegister(name = "eco-host-switch-row", group = "neoecoae", registry = "ldlib2:ui_element")
public class ECOHostSwitchRow extends UIElement {
    public ECOHostSwitchRow() {
        this(Component.empty(), Component.empty(), () -> false, value -> {
        });
    }

    public ECOHostSwitchRow(Component label, Component tooltip, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        addClass("eco-host-switch-row");
        layout(layout -> layout
            .flexDirection(FlexDirection.ROW)
            .justifyContent(AlignContent.SPACE_BETWEEN)
            .alignItems(AlignItems.CENTER)
            .gapAll(4)
            .height(13)
        );
        addChildren(
            new TextElement()
                .setText(label)
                .textStyle(ECOHostStyles::compactValueText)
                .layout(layout -> layout.width(150).height(11)),
            new ECOHostSwitch()
                .bind(DataBindingBuilder.bool(getter, setter).build())
                .layout(layout -> layout.width(24).height(11))
        );

        if (!Component.empty().equals(tooltip)) {
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(tooltip),
                null,
                null,
                null
            ));
        }
    }
}
