package cn.dancingsnow.neoecoae.client;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.config.NEConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes ECO-owned crafting confirmation menus to the ECO report screen. */
public final class ECOCraftConfirmScreenIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private ECOCraftConfirmScreenIntegration() {
    }

    /**
     * Replaces the native confirmation page during screen initialization, before the first frame is rendered.
     * The planner-available flag is synchronized when the menu is opened, so the ECO page can show its own
     * calculating state while the asynchronous plan is still being built.
     */
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        route(event.getScreen(), false);
    }

    /** Fallback for unusual packet timing where the planner flag arrives after screen initialization. */
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        route(event.getScreen(), true);
    }

    private static void route(Screen screen, boolean renderFallback) {
        // Error/CPU-selection sub-screens share this menu and must retain their own lifecycle.
        if (screen instanceof ECOCraftConfirmScreen
                || Minecraft.getInstance().screen != screen
                || !(screen instanceof CraftConfirmScreen container)
                || !(container.getMenu() instanceof CraftConfirmMenu menu)
                || !((Object) menu instanceof ECOCraftConfirmMenuMode mode)
                || (!mode.neoecoae$isEcoPlannerAvailable()
                    && (!renderFallback || !mode.neoecoae$isEcoReportReady()))) {
            return;
        }

        ScreenStyle style = StyleManager.loadStyleDoc("/screens/eco_craft_confirm.json");
        if (NEConfig.ecoCraftConfirmDebug) {
            LOGGER.info("[craft-confirm-route] Client opening ECO screen directly; plannerAvailable={}, reportReady={}",
                mode.neoecoae$isEcoPlannerAvailable(), mode.neoecoae$isEcoReportReady());
        }
        Minecraft.getInstance().setScreen(new ECOCraftConfirmScreen(
                menu, menu.getPlayerInventory(), screen.getTitle(), style));
    }

}
