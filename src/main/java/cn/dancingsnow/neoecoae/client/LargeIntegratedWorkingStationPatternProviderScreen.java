package cn.dancingsnow.neoecoae.client;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The copied ExtendedAE pattern-provider screen with NeoECOAE-owned resources. */
public class LargeIntegratedWorkingStationPatternProviderScreen
    extends PatternProviderScreen<LargeIntegratedWorkingStationPatternProviderMenu> {

    public LargeIntegratedWorkingStationPatternProviderScreen(
        LargeIntegratedWorkingStationPatternProviderMenu menu,
        Inventory playerInventory,
        Component title,
        ScreenStyle style
    ) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        // EAEP adds these private widgets to the superclass, including after a resize.
        hideSmartDoublingWidget("eap$SmartDoublingToggle");
        hideSmartDoublingWidget("eap$PerProviderLimitInput");
    }

    private void hideSmartDoublingWidget(String name) {
        try {
            var field = PatternProviderScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            if (field.get(this) instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.visible = false;
                widget.active = false;
                widget.setFocused(false);
                removeWidget(widget);
            }
        } catch (NoSuchFieldException absent) {
            // EAEP is optional.
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot hide workstation smart doubling", failure);
        }
    }
}
