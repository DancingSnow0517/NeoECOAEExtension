package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

public final class NEAeIconButtonCanvas extends NEAeWidgetCanvas {
    private final NEAeSprite icon;

    public NEAeIconButtonCanvas(NEAeSprite icon) {
        super(18, 20);
        this.icon = icon;
    }

    @Override
    protected void drawWidget(GUIContext context) {
        drawToolbarIconButton(context, 1, 0, icon, hovered(context, 0, 0, 18, 20));
    }
}
