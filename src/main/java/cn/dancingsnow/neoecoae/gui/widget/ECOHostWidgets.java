package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.ScrollDisplay;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.ScrollerMode;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Scroller;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ScrollerView;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;

public final class ECOHostWidgets {
    private ECOHostWidgets() {
    }

    public static ScrollerView storagePanel(int width, int height, int padding, int gap, int scrollbarOffset) {
        ScrollerView panel = new ECOHostChannelScrollerView()
            .scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .horizontalScrollDisplay(ScrollDisplay.NEVER))
            .viewContainer(view -> {
                view.getLayoutStyle().gapAll(gap);
                view.getLayoutStyle().paddingAll(padding);
            })
            .viewPort(view -> view.style(style -> style.backgroundTexture(NETextures.HOST_PANEL_BORDER)))
            .verticalScroller(scroller -> styleStorageScrollbar(scroller, scrollbarOffset));
        panel.layout(layout -> layout.height(height).width(width));
        return panel;
    }

    private static void styleStorageScrollbar(Scroller scroller, int horizontalOffset) {
        scroller.layout(layout -> {
            layout.marginLeft(horizontalOffset);
            layout.marginRight(-horizontalOffset);
            layout.width(ECOHostChannelScrollerView.THUMB_WIDTH);
        });
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
}
