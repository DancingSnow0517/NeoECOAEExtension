package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingPatternBusMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the ECO Crafting Pattern Bus.
 */
public class NECraftingPatternBusScreen extends NEBaseMachineScreen<NECraftingPatternBusMenu> {

    public NECraftingPatternBusScreen(NECraftingPatternBusMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.CRAFTING_PATTERN_BUS);
    }
}
