package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;

/**
 * Menu for the ECO Integrated Working Station — Phase 4 proof of concept.
 * <p>
 * No machine slots, no data slots, no sync. Just opens a screen
 * showing the machine name and a test button.
 * </p>
 */
public class NEIntegratedWorkingStationMenu extends NEBaseMachineMenu {

    public NEIntegratedWorkingStationMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.INTEGRATED_WORKING_STATION.get(), containerId, playerInv, machinePos);
    }
}
