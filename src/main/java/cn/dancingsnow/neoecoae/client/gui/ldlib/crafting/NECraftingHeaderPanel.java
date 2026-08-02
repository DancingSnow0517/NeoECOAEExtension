package cn.dancingsnow.neoecoae.client.gui.ldlib.crafting;

import static cn.dancingsnow.neoecoae.gui.ldlib.crafting.NECraftingLayout.*;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import net.minecraft.network.chat.Component;

/** Draws the two-line crafting title and network/runtime status used by the 1.21.1 host panel. */
public final class NECraftingHeaderPanel {
    private static final int SUCCESS_COLOR = 0xFF00A850;
    private static final int ERROR_COLOR = 0xFFC03434;
    private static final int MUTED_COLOR = 0xFF606060;
    private static final int WARNING_COLOR = 0xFFE08A24;

    public void draw(NECraftingRenderContext context, Component title, NECraftingUiState state) {
        int rightLimit = TOOLBAR_X - 8;
        context.drawFitted(title, HEADER_TITLE_X, HEADER_TITLE_Y, rightLimit - HEADER_TITLE_X, 0xFF4A4A4A);

        int modeX = HEADER_TITLE_X;
        context.drawFitted(networkModeText(state), modeX, HEADER_STATUS_Y, HEADER_MODE_W, networkModeColor(state));
        int connectionX = modeX + HEADER_MODE_W + HEADER_GAP;
        context.drawFitted(
                networkConnectionText(state),
                connectionX,
                HEADER_STATUS_Y,
                HEADER_CONNECTION_W,
                state.networkConnected() ? SUCCESS_COLOR : ERROR_COLOR);
        int runStatusX = connectionX + HEADER_CONNECTION_W + HEADER_GAP + 1;
        context.drawFitted(
                runStatusText(state.runStatus()),
                runStatusX,
                HEADER_STATUS_Y,
                Math.max(0, rightLimit - runStatusX),
                state.runStatus() == 0 ? SUCCESS_COLOR : WARNING_COLOR);
    }

    private static Component networkModeText(NECraftingUiState state) {
        String key = state.networkMultiplier() >= 8
                ? "gui.neoecoae.host.network.mode.high_energy"
                : state.networkMultiplier() >= 2
                        ? "gui.neoecoae.host.network.mode.normal"
                        : "gui.neoecoae.host.network.mode.local";
        return Component.translatable(key);
    }

    private static Component networkConnectionText(NECraftingUiState state) {
        return Component.translatable(
                state.networkConnected()
                        ? "gui.neoecoae.host.network.connected"
                        : "gui.neoecoae.host.network.disconnected");
    }

    private static Component runStatusText(int status) {
        String key =
                switch (status) {
                    case 1 -> "gui.neoecoae.crafting.run_status.missing_coolant";
                    case 2 -> "gui.neoecoae.crafting.run_status.missing_energy";
                    case 3 -> "gui.neoecoae.crafting.run_status.overclock_mismatch";
                    case 4 -> "gui.neoecoae.crafting.run_status.network_coolant_incompatible";
                    default -> "gui.neoecoae.crafting.run_status.normal";
                };
        return Component.translatable(key);
    }

    private static int networkModeColor(NECraftingUiState state) {
        return state.networkMultiplier() <= 1 ? MUTED_COLOR : SUCCESS_COLOR;
    }
}
