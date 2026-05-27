package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;

/**
 * Menu for the ECO Computation Controller — Phase 2 proof of concept.
 * <p>
 * No machine slots, no data slots, no sync. Just opens a screen
 * showing the machine name and a test button.
 * </p>
 */
public class NEComputationControllerMenu extends NEBaseMachineMenu {

    public NEComputationControllerMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.COMPUTATION_CONTROLLER.get(), containerId, playerInv, machinePos);
    }
}
