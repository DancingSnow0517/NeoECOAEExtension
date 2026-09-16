package cn.dancingsnow.neoecoae.client;

import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.network.ECOPlanRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Client-only ECO entry point shared by AE2-native and third-party confirmation screens. */
public final class ECOCraftConfirmScreenIntegration {
    private static final int TOOLBAR_BUTTON_X_OFFSET = -18;
    private static final int TOOLBAR_BUTTON_Y_OFFSET = 2;
    /** Menus for which the automatic ECO request has already been sent. */
    private static final Set<CraftConfirmMenu> AUTO_REQUESTED_MENUS =
        Collections.newSetFromMap(new WeakHashMap<>());

    private ECOCraftConfirmScreenIntegration() {
    }

    public static IconButton createPlannerButton(CraftConfirmMenu menu) {
        return new EcoPlannerButton(() -> requestEcoPlan(menu));
    }

    public static void updatePlannerButton(IconButton button, CraftConfirmMenu menu) {
        if (!((Object) menu instanceof ECOCraftConfirmMenuMode mode)) {
            button.setVisibility(false);
            return;
        }
        button.setVisibility(mode.neoecoae$isEcoPlannerAvailable() && !mode.neoecoae$isEcoReportReady());
    }

    public static void requestEcoPlan(CraftConfirmMenu menu) {
        if (!((Object) menu instanceof ECOCraftConfirmMenuMode mode)
                || !mode.neoecoae$isEcoPlannerAvailable()
                || mode.neoecoae$isEcoReportReady()) {
            return;
        }
        synchronized (AUTO_REQUESTED_MENUS) {
            if (!AUTO_REQUESTED_MENUS.add(menu)) {
                return;
            }
        }
        PacketDistributor.sendToServer(new ECOPlanRequestPayload(menu.containerId));
    }

    /**
     * Starts ECO as soon as a confirmation page is shown. The request is client-side only and is de-duplicated per
     * menu because render events run every frame. The server-side handler cancels AE2's native planning before
     * starting the ECO job, so the two planners cannot race to publish a result.
     */
    private static void requestEcoPlanAutomatically(CraftConfirmMenu menu) {
        if (!((Object) menu instanceof ECOCraftConfirmMenuMode mode)
                || !mode.neoecoae$isEcoPlannerAvailable()
                || mode.neoecoae$isEcoReportReady()) {
            return;
        }
        requestEcoPlan(menu);
    }

    /** Adds the ECO action to custom confirmation pages such as DataEnergistics' Trinity screen. */
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> container)
                || !(container.getMenu() instanceof CraftConfirmMenu menu)
                || !((Object) menu instanceof ECOCraftConfirmMenuMode)) {
            return;
        }

        requestEcoPlanAutomatically(menu);

        if (screen instanceof CraftConfirmScreen || screen instanceof ECOCraftConfirmScreen) {
            return;
        }

        IconButton button = createPlannerButton(menu);
        button.setX(container.getGuiLeft() + TOOLBAR_BUTTON_X_OFFSET);
        button.setY(container.getGuiTop() + TOOLBAR_BUTTON_Y_OFFSET);
        updatePlannerButton(button, menu);
        event.addListener(button);
    }

    /** Supplies the tooltip for custom screens that do not implement AE2's toolbar tooltip pass. */
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> container)
                || !(container.getMenu() instanceof CraftConfirmMenu menu)
                || !((Object) menu instanceof ECOCraftConfirmMenuMode)) {
            return;
        }

        requestEcoPlanAutomatically(menu);
        if (screen instanceof CraftConfirmScreen || screen instanceof ECOCraftConfirmScreen) {
            return;
        }
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof EcoPlannerButton button) {
                updatePlannerButton(button, menu);
            }
            if (listener instanceof EcoPlannerButton button && button.visible
                    && button.isMouseOver(event.getMouseX(), event.getMouseY())) {
                screen.setTooltipForNextRenderPass(button.getMessage());
                return;
            }
        }
    }

    /** Switches a custom confirmation page only after the server has published the ECO report. */
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof CraftConfirmScreen || screen instanceof ECOCraftConfirmScreen
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

    private static final class EcoPlannerButton extends IconButton {
        private EcoPlannerButton(Runnable onPress) {
            super(ignored -> onPress.run());
            setMessage(Component.translatable("gui.neoecoae.crafting.fast_planner.on"));
        }

        @Override
        protected Icon getIcon() {
            return Icon.CRAFT_HAMMER;
        }
    }
}
