package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

@LDLRegister(name = "eco-host-metric", group = "neoecoae", registry = "ldlib2:ui_element")
public class ECOHostMetric extends UIElement {
    public ECOHostMetric() {
        this(Component::empty, Component::empty, null);
    }

    public ECOHostMetric(Supplier<Component> label, Supplier<Component> value, Supplier<Float> ratio) {
        addClass("eco-host-metric");
        layout(layout -> layout
            .width(132)
            .height(72)
            .paddingAll(5)
            .gapAll(4)
        );
        addChildren(
            new Label()
                .bindDataSource(SupplierDataSource.of(label))
                .textStyle(ECOHostStyles::labelText)
                .layout(layout -> layout.height(18)),
            new Label()
                .bindDataSource(SupplierDataSource.of(value))
                .textStyle(ECOHostStyles::valueText)
                .layout(layout -> layout.height(30)),
            ratio == null ? spacer() : progress(ratio)
        );
    }

    public static ECOHostMetric ratio(Supplier<Component> label, Supplier<Component> value, Supplier<Float> ratio) {
        return new ECOHostMetric(label, value, ratio);
    }

    public static ECOHostMetric scalar(Supplier<Component> label, Supplier<Component> value) {
        return new ECOHostMetric(label, value, null);
    }

    private static UIElement spacer() {
        return new UIElement()
            .addClass("eco-host-metric-spacer")
            .layout(layout -> layout.height(5).widthPercent(100));
    }

    private static UIElement progress(Supplier<Float> ratio) {
        return new ProgressBar()
            .label(label -> label.setText(""))
            .bind(DataBindingBuilder.floatValS2C(ratio::get).build())
            .layout(layout -> layout.height(5).widthPercent(100));
    }
}
