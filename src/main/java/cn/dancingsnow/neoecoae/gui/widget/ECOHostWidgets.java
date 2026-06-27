package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NETextures;
import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
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
        return hostPanel(title, subtitle, state, metrics, details, footerHint, buildWindow, ECOHostStyles.PANEL_HEIGHT);
    }

    public static UIElement hostPanel(
        Supplier<Component> title,
        Supplier<Component> subtitle,
        Supplier<Component> state,
        List<ECOHostMetric> metrics,
        UIElement details,
        Supplier<Component> footerHint,
        UIElement buildWindow,
        int panelHeight
    ) {
        UIElement root = new UIElement().layout(layout -> {
            layout.width(ECOHostStyles.PANEL_WIDTH);
            layout.height(panelHeight);
            layout.paddingAll(6);
            layout.gapAll(4);
        }).addClass("eco-host-panel");

        root.addChild(header(title, subtitle, state));
        root.addChild(metricRow(metrics));
        root.addChild(details.layout(layout -> layout.widthPercent(100)));
        root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        root.addChild(buildWindow);
        return root;
    }

    public static UIElement header(Supplier<Component> title, Supplier<Component> subtitle, Supplier<Component> state) {
        UIElement header = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(5);
            layout.height(16);
        }).addClass("eco-host-header");

        UIElement titleBox = new UIElement().layout(layout -> {
            layout.width(133);
        });
        titleBox.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(title))
            .textStyle(ECOHostStyles::titleText));
        header.addChild(titleBox);

        return header;
    }

    public static UIElement metricRow(List<ECOHostMetric> metrics) {
        UIElement row = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(2);
            layout.height(91);
        }).addClass("eco-host-metrics");
        metrics.forEach(row::addChild);
        return row;
    }

    public static UIElement detailArea(boolean scroll) {
        return detailArea(scroll, scroll ? ECOHostStyles.STORAGE_DETAIL_HEIGHT : ECOHostStyles.DETAIL_HEIGHT);
    }

    public static UIElement detailArea(boolean scroll, int height) {
        if (!scroll) {
            return new UIElement().layout(layout -> {
                layout.gapAll(4);
                layout.height(height);
            }).addClass("eco-host-details");
        }

        ScrollerView scroller = new ScrollerView()
            .viewPort(view -> view.layout(layout -> layout.paddingAll(0)).addClass("eco-host-scroll-port"))
            .viewContainer(view -> view.getLayout().gapAll(4));
        scroller.layout(layout -> layout.height(height));
        scroller.addClass("eco-host-details");
        return scroller;
    }

    public static UIElement storageDetailArea(UIElement scrollList) {
        UIElement details = new UIElement().layout(layout -> {
            layout.gapAll(3);
            layout.height(ECOHostStyles.STORAGE_DETAIL_HEIGHT);
        }).addClass("eco-host-details");
        details.addChild(sectionTitle("gui.neoecoae.host.storage.channels"));
        details.addChild(scrollList.layout(layout -> {
            layout.widthPercent(100);
            layout.height(ECOHostStyles.STORAGE_DETAIL_HEIGHT - 13);
        }));
        return details;
    }

    public static ScrollerView scrollList(int height) {
        ScrollerView scrollerView = new ECOHostChannelScrollerView()
            .viewPort(view -> view.layout(layout -> layout.paddingAll(0)).addClass("eco-host-scroll-port"))
            .viewContainer(view -> view.getLayout().gapAll(4))
            .verticalScroller(ECOHostWidgets::styleStorageScrollbar);
        scrollerView.layout(layout -> layout.height(height));
        scrollerView.addClass("eco-host-scroll-list");
        return scrollerView;
    }

    private static void styleStorageScrollbar(Scroller scroller) {
        scroller.layout(layout -> layout.width(ECOHostChannelScrollerView.THUMB_WIDTH));
        scroller.headButton(button -> button.setDisplay(false));
        scroller.tailButton(button -> button.setDisplay(false));
        scroller.scrollContainer(container -> {
            container.layout(layout -> {
                layout.marginLeft(3);
                layout.width(6);
            });
            container.style(style -> style.backgroundTexture(NETextures.AE_SCROLLBAR_TRACK));
        });
        scroller.scrollBar(button -> button
            .noText()
            .buttonStyle(style -> style
                .baseTexture(IGuiTexture.EMPTY)
                .hoverTexture(IGuiTexture.EMPTY)
                .pressedTexture(IGuiTexture.EMPTY))
            .style(style -> style.backgroundTexture(IGuiTexture.EMPTY))
            .layout(layout -> {
                layout.marginLeft(-3);
                layout.width(ECOHostChannelScrollerView.THUMB_WIDTH);
            })
            .addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> dragStorageScrollbar(scroller, event)));
    }

    private static void dragStorageScrollbar(Scroller scroller, UIEvent event) {
        if (event.dragHandler == null || !(event.dragHandler.draggingObject instanceof Float initialValue)) {
            return;
        }
        float trackHeight = scroller.scrollContainer.getContentHeight();
        float remainingSpace = Math.max(1, trackHeight - ECOHostChannelScrollerView.THUMB_HEIGHT);
        float deltaY = scroller.getLocalMouse(event.x, event.y).y - scroller.getLocalMouse(event.dragStartX, event.dragStartY).y;
        float valueRange = scroller.getMaxValue() - scroller.getMinValue();
        scroller.setValue(initialValue + deltaY / remainingSpace * valueRange);
        event.stopImmediatePropagation();
    }

    public static Label sectionTitle(String key) {
        Label label = new Label();
        label.setText(Component.translatable(key));
        label.textStyle(ECOHostStyles::sectionText);
        label.layout(layout -> layout.height(10));
        return label;
    }

    public static UIElement card() {
        return new UIElement().layout(layout -> {
            layout.paddingAll(3);
            layout.gapAll(2);
        }).addClass("eco-host-card");
    }

    public static UIElement tile(String key, Supplier<Component> value) {
        UIElement tile = new UIElement().layout(layout -> {
            layout.width(96);
            layout.height(26);
            layout.paddingAll(3);
            layout.gapAll(1);
        }).addClass("eco-host-tile");
        tile.addChild(new Label()
            .setText(Component.translatable(key))
            .textStyle(ECOHostStyles::compactLabelText));
        Label valueLabel = new Label();
        valueLabel.bind(DataBindingBuilder.componentS2C(value).build());
        valueLabel.textStyle(ECOHostStyles::compactValueText);
        tile.addChild(valueLabel);
        return tile;
    }

    public static UIElement tileRow(List<UIElement> tiles) {
        UIElement grid = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(3);
            layout.height(55);
        });
        for (int i = 0; i < tiles.size(); i += 2) {
            UIElement row = new UIElement().layout(layout -> {
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(4);
                layout.height(26);
            });
            row.addChild(tiles.get(i));
            if (i + 1 < tiles.size()) {
                row.addChild(tiles.get(i + 1));
            }
            grid.addChild(row);
        }
        return grid;
    }

    public static UIElement statLine(String key, Supplier<Component> value, Supplier<Float> ratio, String tooltipTitleKey) {
        return statLineWithTooltipTitle(key, value, ratio, tooltipTitleKey);
    }

    public static UIElement statLine(String key, Supplier<Component> value, Supplier<Float> ratio) {
        return statLineWithTooltipTitle(key, value, ratio, key);
    }

    private static UIElement statLineWithTooltipTitle(
        String key,
        Supplier<Component> value,
        Supplier<Float> ratio,
        String tooltipTitleKey
    ) {
        UIElement row = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(1);
            layout.height(18);
        }).addClass("eco-host-stat-line");
        row.addChild(new Label()
            .setText(Component.translatable(key))
            .textStyle(ECOHostStyles::hostStatTitleText)
            .layout(layout -> layout.widthPercent(100).height(8)));
        TooltipCache tooltipCache = new TooltipCache();
        SyncValue<Component> tooltipSync = DataBindingBuilder.componentS2C(value).build().getSyncValue();
        tooltipSync.addListener(component -> tooltipCache.set(Component.translatable(tooltipTitleKey), component));
        row.addSyncValue(tooltipSync);
        row.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            event.hoverTooltips = new HoverTooltips(tooltipCache.lines(), null, null, null);
            event.stopPropagation();
        });
        UIElement valueRow = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(4);
            layout.height(9);
        });
        valueRow.addChild(new ProgressBar()
            .label(label -> label.setText(""))
            .barContainer(element -> element.layout(layout -> layout.paddingAll(1)))
            .bind(DataBindingBuilder.floatValS2C(ratio::get).build())
            .layout(layout -> layout.width(58).height(4))
            .addClass("eco-host-progress"));
        Label valueLabel = new Label();
        valueLabel.bind(DataBindingBuilder.componentS2C(value).build());
        valueLabel.textStyle(ECOHostStyles::hostStatValueText);
        valueLabel.layout(layout -> layout.flexGrow(1));
        valueRow.addChild(valueLabel);
        row.addChild(valueRow);
        return row;
    }

    private static final class TooltipCache {
        private List<Component> lines = List.of();

        private void set(Component title, Component value) {
            this.lines = List.of(title, value == null ? Component.empty() : value.copy().withStyle(ChatFormatting.WHITE));
        }

        private List<Component> lines() {
            return lines;
        }
    }

    public static UIElement footer(Supplier<Component> hint) {
        UIElement footer = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(4);
            layout.height(11);
        }).addClass("eco-host-footer");
        footer.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(hint))
            .textStyle(ECOHostStyles::hintText)
            .layout(layout -> layout.width(184)));
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
