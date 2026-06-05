package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.NETextures;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

@LDLRegister(name = "eco-host-switch", group = "neoecoae", registry = "ldlib2:ui_element")
public class ECOHostSwitch extends Switch {
    public ECOHostSwitch() {
        placeholder.setDisplay(false);
        markIcon.setDisplay(false);
        getLayout().width(22).height(12);
    }

    @Override
    public void drawBackgroundTexture(GUIContext guiContext) {
        guiContext.drawTexture(selectTexture(isOn(), isHover()), getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }

    @Override
    public void drawContents(GUIContext guiContext) {
    }

    static IGuiTexture selectTexture(boolean isOn, boolean isHover) {
        if (isOn) {
            return isHover ? NETextures.SWITCH_ON_HOVER : NETextures.SWITCH_ON;
        }
        return isHover ? NETextures.SWITCH_OFF_HOVER : NETextures.SWITCH_OFF;
    }
}
