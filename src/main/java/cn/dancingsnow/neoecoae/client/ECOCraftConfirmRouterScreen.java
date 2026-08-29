package cn.dancingsnow.neoecoae.client;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.ECOCraftConfirmMenuMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Keeps AE2's native report unless the server explicitly enables the ECO fast planner for this menu. */
public final class ECOCraftConfirmRouterScreen extends CraftConfirmScreen {
    private final Inventory playerInventory;
    private final Component screenTitle;
    private boolean routed;

    public ECOCraftConfirmRouterScreen(CraftConfirmMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.playerInventory = playerInventory;
        this.screenTitle = title;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (!routed
                && (Object) menu instanceof ECOCraftConfirmMenuMode mode
                && mode.neoecoae$shouldShowFastPlannerReport()) {
            routed = true;
            switchToScreen(new ECOCraftConfirmScreen(menu, playerInventory, screenTitle,
                StyleManager.loadStyleDoc("/screens/eco_craft_confirm.json")));
        }
    }
}
