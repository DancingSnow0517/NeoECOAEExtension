package cn.dancingsnow.neoecoae.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.config.NEConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes a completed ECO result to its report screen without initiating server-side planning. */
public final class ECOCraftConfirmScreenIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

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
        if (NEConfig.ecoCraftConfirmDebug) {
            LOGGER.info("[craft-confirm-route] Client received ecoReportReady=true; switching {} to ECO screen",
                screen.getClass().getName());
        }
        Minecraft.getInstance().setScreen(new ECOCraftConfirmScreen(
                menu, menu.getPlayerInventory(), screen.getTitle(), style));
    }

}
