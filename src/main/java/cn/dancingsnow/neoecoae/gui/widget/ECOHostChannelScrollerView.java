package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

@LDLRegister(name = "eco-host-channel-scroller-view", group = "neoecoae", registry = "ldlib2:ui_element")
public class ECOHostChannelScrollerView extends ScrollerView {
    static final float THUMB_HEIGHT = 15;
    static final float THUMB_WIDTH = 12;

    @Override
    public void drawContents(GUIContext guiContext) {
        syncNativeThumbSize();
        super.drawContents(guiContext);
    }

    @Override
    public void drawBackgroundOverlay(GUIContext guiContext) {
        super.drawBackgroundOverlay(guiContext);
        if (!verticalScroller.isDisplayed()) {
            return;
        }
        float trackHeight = verticalScroller.scrollContainer.getContentHeight();
        if (trackHeight <= 0) {
            return;
        }
        float scrollRange = Math.max(0, trackHeight - THUMB_HEIGHT);
        float thumbY = verticalScroller.scrollContainer.getPositionY()
            + verticalScroller.getValue() * scrollRange;
        guiContext.drawTexture(
            NETextures.AE_SCROLLBAR_THUMB,
            verticalScroller.getPositionX(),
            thumbY,
            THUMB_WIDTH,
            THUMB_HEIGHT
        );
    }

    private void syncNativeThumbSize() {
        if (!verticalScroller.isDisplayed()) {
            return;
        }
        float trackHeight = verticalScroller.scrollContainer.getContentHeight();
        if (trackHeight <= 0) {
            return;
        }
        float thumbPercent = Math.min(100, THUMB_HEIGHT / trackHeight * 100);
        verticalScroller.setScrollBarSize(thumbPercent);
    }
}
