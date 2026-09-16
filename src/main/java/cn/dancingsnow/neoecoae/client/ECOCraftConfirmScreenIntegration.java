package cn.dancingsnow.neoecoae.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Routes a completed ECO result to its report screen without initiating server-side planning. */
public final class ECOCraftConfirmScreenIntegration {
    private ECOCraftConfirmScreenIntegration() {
    }

    /**
     * Switches a confirmation page only after the server has published the ECO report.
     *
     * The server sets report-ready only when the exact plan object carries ECO diagnostics, so a third-party plan
     * can never be routed here even if it uses the same menu type.
     */
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof ECOCraftConfirmScreen
                || Minecraft.getInstance().screen != screen
                || !(screen instanceof AbstractContainerScreen<?> container)
                || !(container.getMenu() instanceof CraftConfirmMenu menu)
                || !((Object) menu instanceof ECOCraftConfirmMenuMode mode)
                || !mode.neoecoae$isEcoReportReady()) {
            return;
        }

        ScreenStyle style = StyleManager.loadStyleDoc("/screens/eco_craft_confirm.json");
        Minecraft.getInstance().setScreen(new ECOCraftConfirmScreen(
                menu, menu.getPlayerInventory(), screen.getTitle(), style));
    }

}
