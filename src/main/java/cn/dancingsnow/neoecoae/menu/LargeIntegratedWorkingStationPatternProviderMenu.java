package cn.dancingsnow.neoecoae.menu;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.PatternProviderMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** AE2 menu layout backed by the workstation provider's 36 slots. */
public class LargeIntegratedWorkingStationPatternProviderMenu extends PatternProviderMenu {
    public static final MenuType<LargeIntegratedWorkingStationPatternProviderMenu> TYPE = MenuTypeBuilder.create(
                    LargeIntegratedWorkingStationPatternProviderMenu::new, PatternProviderLogicHost.class)
            .build("neoecoae_large_integrated_working_station_interface");

    protected LargeIntegratedWorkingStationPatternProviderMenu(
            int id, Inventory inventory, PatternProviderLogicHost host) {
        super(TYPE, id, inventory, host);
    }
}
