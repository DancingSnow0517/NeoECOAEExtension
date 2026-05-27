package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the ECO Integrated Working Station.
 */
public class NEIntegratedWorkingStationScreen extends NEBaseMachineScreen<NEIntegratedWorkingStationMenu> {

    public NEIntegratedWorkingStationScreen(NEIntegratedWorkingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.INTEGRATED_WORKING_STATION);
    }
}
