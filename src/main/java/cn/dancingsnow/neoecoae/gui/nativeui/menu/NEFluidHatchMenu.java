package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;

/**
 * Menu for ECO Fluid Input/Output Hatches — Phase 6 proof of concept.
 * <p>
 * A single generic menu serves both input and output hatches.
 * No machine slots, no data slots, no sync.
 * </p>
 */
public class NEFluidHatchMenu extends NEBaseMachineMenu {

    public NEFluidHatchMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.FLUID_HATCH.get(), containerId, playerInv, machinePos);
    }
}
