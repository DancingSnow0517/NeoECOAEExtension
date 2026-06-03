package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

public final class ECOHostWidgets {
    private ECOHostWidgets() {
    }

    public static UIElement hostPanel(
        Supplier<Component> title,
        Supplier<Component> subtitle,
        Supplier<Component> state,
        List<ECOHostMetric> metrics,
        UIElement details,
        Supplier<Component> footerHint,
        UIElement buildWindow
    ) {
        UIElement root = new UIElement().layout(layout -> {
            layout.width(ECOHostStyles.PANEL_WIDTH);
            layout.height(ECOHostStyles.PANEL_HEIGHT);
            layout.paddingAll(10);
            layout.gapAll(7);
        }).addClass("eco-host-panel");

        root.addChild(header(title, subtitle, state));
        root.addChild(metricRow(metrics));
        root.addChild(details.layout(layout -> layout.widthPercent(100)));
        root.addChild(footer(footerHint, buildWindow));
        root.addChild(buildWindow);
        return root;
    }

    public static UIElement header(Supplier<Component> title, Supplier<Component> subtitle, Supplier<Component> state) {
        UIElement header = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(8);
            layout.height(46);
        }).addClass("eco-host-header");

        UIElement titleBox = new UIElement().layout(layout -> {
            layout.gapAll(2);
            layout.width(320);
        });
        titleBox.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(title))
            .textStyle(ECOHostStyles::titleText));
        titleBox.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(subtitle))
            .textStyle(ECOHostStyles::subtitleText));
        header.addChild(titleBox);

        header.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(state))
            .textStyle(ECOHostStyles::sectionText)
            .layout(layout -> layout.width(82).height(22).paddingAll(3))
            .addClass("eco-host-status"));

        return header;
    }

    public static UIElement metricRow(List<ECOHostMetric> metrics) {
        UIElement row = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(7);
            layout.height(72);
        }).addClass("eco-host-metrics");
        metrics.forEach(row::addChild);
        return row;
    }

    public static UIElement detailArea(boolean scroll) {
        if (!scroll) {
            return new UIElement().layout(layout -> {
                layout.gapAll(5);
                layout.height(209);
            }).addClass("eco-host-details");
        }

        ScrollerView scroller = new ScrollerView().viewContainer(view -> view.getLayout().gapAll(5));
        scroller.layout(layout -> layout.height(209));
        scroller.addClass("eco-host-details");
        return scroller;
    }

    public static Label sectionTitle(String key) {
        Label label = new Label();
        label.setText(Component.translatable(key));
        label.textStyle(ECOHostStyles::sectionText);
        label.layout(layout -> layout.height(14));
        return label;
    }

    public static UIElement card() {
        return new UIElement().layout(layout -> {
            layout.paddingAll(6);
            layout.gapAll(5);
        }).addClass("eco-host-card");
    }

    public static UIElement tile(String key, Supplier<Component> value) {
        UIElement tile = new UIElement().layout(layout -> {
            layout.width(100);
            layout.height(40);
            layout.paddingAll(5);
            layout.gapAll(2);
        }).addClass("eco-host-tile");
        tile.addChild(new Label()
            .setText(Component.translatable(key))
            .textStyle(ECOHostStyles::labelText));
        tile.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(value))
            .textStyle(ECOHostStyles::valueText));
        return tile;
    }

    public static UIElement tileRow(List<UIElement> tiles) {
        UIElement row = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(5);
            layout.height(42);
        });
        tiles.forEach(row::addChild);
        return row;
    }

    public static UIElement statLine(String key, Supplier<Component> value, Supplier<Float> ratio) {
        UIElement row = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(5);
            layout.height(12);
        }).addClass("eco-host-stat-line");
        row.addChild(new Label()
            .setText(Component.translatable(key))
            .textStyle(ECOHostStyles::labelText)
            .layout(layout -> layout.width(54)));
        row.addChild(new ProgressBar()
            .label(label -> label.setText(""))
            .barContainer(element -> element.layout(layout -> layout.paddingAll(1)))
            .bind(DataBindingBuilder.floatValS2C(ratio::get).build())
            .layout(layout -> layout.width(210).height(5)));
        row.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(value))
            .textStyle(ECOHostStyles::valueText)
            .layout(layout -> layout.width(82)));
        return row;
    }

    public static UIElement footer(Supplier<Component> hint, UIElement buildWindow) {
        UIElement footer = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(8);
            layout.height(26);
        }).addClass("eco-host-footer");
        footer.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(hint))
            .textStyle(ECOHostStyles::hintText)
            .layout(layout -> layout.width(390)));
        footer.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        return footer;
    }

    public static void addDetailChild(UIElement details, UIElement child) {
        if (details instanceof ScrollerView scroller) {
            scroller.addScrollViewChild(child);
        } else {
            details.addChild(child);
        }
    }
}
