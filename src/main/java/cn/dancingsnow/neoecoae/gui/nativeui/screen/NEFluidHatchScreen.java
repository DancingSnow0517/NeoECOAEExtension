package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEFluidHatchMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for ECO Fluid Input/Output Hatches.
 * <p>
 * A single generic screen serves both input and output hatches.
 * The machine display name (e.g. "ECO Fluid Input Hatch") distinguishes them
 * in the title and test-button log.
 * </p>
 */
public class NEFluidHatchScreen extends NEBaseMachineScreen<NEFluidHatchMenu> {

    public NEFluidHatchScreen(NEFluidHatchMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.FLUID_HATCH);
    }

    @Override
    protected String getTestLogMessage() {
        return config.buildLogMessage(title);
    }
}
