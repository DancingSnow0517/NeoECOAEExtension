package cn.dancingsnow.neoecoae.menu;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.PatternProviderMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** The copied ExtendedAE-style menu for the large workstation interface. */
public class LargeIntegratedWorkingStationPatternProviderMenu extends PatternProviderMenu {
    public static final MenuType<LargeIntegratedWorkingStationPatternProviderMenu> TYPE = MenuTypeBuilder
        .create(LargeIntegratedWorkingStationPatternProviderMenu::new, PatternProviderLogicHost.class)
        .buildUnregistered(NeoECOAE.id("large_integrated_working_station_interface"));

    protected LargeIntegratedWorkingStationPatternProviderMenu(
        int id,
        Inventory playerInventory,
        PatternProviderLogicHost host
    ) {
        super(TYPE, id, playerInventory, host);
    }
}
