package cn.dancingsnow.neoecoae.client;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class LargeIntegratedWorkingStationPatternProviderScreen
        extends PatternProviderScreen<LargeIntegratedWorkingStationPatternProviderMenu> {
    public LargeIntegratedWorkingStationPatternProviderScreen(
            LargeIntegratedWorkingStationPatternProviderMenu menu,
            Inventory inventory,
            Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
    }
}
