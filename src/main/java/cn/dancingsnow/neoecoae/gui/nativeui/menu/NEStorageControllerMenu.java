package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;

/**
 * Menu for the ECO Storage Controller — Phase 1 proof of concept.
 * <p>
 * No machine slots, no data slots, no sync. Just opens a screen
 * showing the machine name and a test button.
 * </p>
 */
public class NEStorageControllerMenu extends NEBaseMachineMenu {

    public NEStorageControllerMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.STORAGE_CONTROLLER.get(), containerId, playerInv, machinePos);
    }
}
