package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEComputationControllerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the ECO Computation Controller.
 */
public class NEComputationControllerScreen extends NEBaseMachineScreen<NEComputationControllerMenu> {

    public NEComputationControllerScreen(NEComputationControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.COMPUTATION_CONTROLLER);
    }
}
