package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.multiblock.network.NEFrequencyAllocator;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SimpleBinding;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class HostElements {
    private HostElements() {
    }

    /** A hidden value fed by {@code binding}, attached to {@code parent} so it syncs with the UI. */
    public static <T> BindableValue<T> hiddenValue(UIElement parent, T initial, SimpleBinding<T> binding) {
        BindableValue<T> value = new BindableValue<>(initial);
        value.bind(binding);
        value.setDisplay(false);
        parent.addChild(value);
        return value;
    }

    /** As {@link #hiddenValue(UIElement, Object, SimpleBinding)}, also notifying {@code listener} on each value. */
    public static <T> BindableValue<T> hiddenValue(UIElement parent, T initial, SimpleBinding<T> binding,
                                                   Consumer<T> listener) {
        BindableValue<T> value = new BindableValue<>(initial);
        value.bind(binding);
        value.registerValueListener(listener);
        value.setDisplay(false);
        parent.addChild(value);
        return value;
    }

    public static Label textSegment(Supplier<Component> text, IntSupplier color) {
        Label label = new Label();
        Supplier<Component> styledText = () -> text.get().copy().withColor(color.getAsInt());
        label.setText(styledText.get());
        label.bind(DataBindingBuilder.componentS2C(styledText).build());
        label.textStyle(HostElements::lineTextStyle);
        return label;
    }

    public static Component networkFrequencyTooltip(int frequency) {
        return frequency == NEFrequencyAllocator.UNASSIGNED
            ? Component.translatable("gui.neoecoae.host.network_frequency.cycle.unassigned")
            : Component.translatable("gui.neoecoae.host.network_frequency.cycle", frequency);
    }

    public static <T extends UIElement> T tooltips(T element, Component... lines) {
        element.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = HoverTooltips.empty().append(lines));
        return element;
    }

    /** The hover payload for a list of lines, for handlers that assign {@code event.hoverTooltips} directly. */
    public static HoverTooltips hoverTooltips(List<Component> lines) {
        return new HoverTooltips(lines, null, null, null);
    }

    /** Same display as {@link #tooltips(UIElement, Component...)}, for a list built at the call site. */
    public static <T extends UIElement> T tooltips(T element, List<Component> lines) {
        element.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = hoverTooltips(lines));
        return element;
    }

    /** As {@link #tooltips(UIElement, List)}, but the list is evaluated each time the tooltip is shown. */
    public static <T extends UIElement> T tooltips(T element, Supplier<List<Component>> lines) {
        element.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = hoverTooltips(lines.get()));
        return element;
    }

    public static <T extends UIElement> T absolute(T element, int left, int top, int width, int height) {
        element.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(left);
            layout.top(top);
            layout.width(width);
            layout.height(height);
        });
        return element;
    }

    private static void lineTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textShadow(false);
    }
}
