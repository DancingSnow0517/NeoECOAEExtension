package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;

/**
 * Menu for the ECO Crafting Pattern Bus — Phase 5 proof of concept.
 * <p>
 * No machine slots, no data slots, no sync. Just opens a screen
 * showing the machine name and a test button.
 * </p>
 */
public class NECraftingPatternBusMenu extends NEBaseMachineMenu {

    public NECraftingPatternBusMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.CRAFTING_PATTERN_BUS.get(), containerId, playerInv, machinePos);
    }
}
