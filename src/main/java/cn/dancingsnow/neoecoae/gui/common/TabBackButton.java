package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

public class TabBackButton extends Button {
    public TabBackButton() {
        noText();
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        // AE2's box tab changes only with focus and draws both sprites at native size.
        ECOIcon background = isFocused() ? ECOIcon.TAB_BUTTON_BACKGROUND_FOCUS : ECOIcon.TAB_BUTTON_BACKGROUND;
        context.drawTexture(AETextures.icon(background), getPositionX(), getPositionY(), background.width, background.height);
        context.drawTexture(AETextures.icon(ECOIcon.BACK), getPositionX() + 2, getPositionY() + 1, 16, 16);
    }
}
