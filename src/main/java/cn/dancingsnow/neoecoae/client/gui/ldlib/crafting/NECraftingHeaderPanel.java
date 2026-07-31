package cn.dancingsnow.neoecoae.client.gui.ldlib.crafting;

import static cn.dancingsnow.neoecoae.gui.ldlib.crafting.NECraftingLayout.TOOLBAR_X;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import net.minecraft.network.chat.Component;

/** Draws the crafting host title and compact formed/active indicators. */
public final class NECraftingHeaderPanel {
    private static final int LABEL_COLOR = 0xFF5D5D5D;
    private static final int SUCCESS_COLOR = 0xFF00A850;
    private static final int ERROR_COLOR = 0xFFC03434;
    private static final int MUTED_COLOR = 0xFF606060;

    public void draw(NECraftingRenderContext context, Component title, NECraftingUiState state) {
        context.draw(title, 8, 8, 0xFF4A4A4A);

        Component formedLabel =
                Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component formedValue = boolText(state.formed());
        Component activeLabel = Component.literal("  ")
                .append(Component.translatable("gui.neoecoae.machine.active"))
                .append(": ");
        Component activeValue = boolText(state.active());
        Component networkLabel = Component.literal("  ")
                .append(Component.translatable("gui.neoecoae.host.network"))
                .append(": ");
        Component networkValue = networkText(state);
        int textWidth = context.scaledWidth(formedLabel)
                + context.scaledWidth(formedValue)
                + context.scaledWidth(activeLabel)
                + context.scaledWidth(activeValue)
                + context.scaledWidth(networkLabel)
                + context.scaledWidth(networkValue);
        int titleRight = 8 + context.scaledWidth(title) + 10;
        int rightLimit = TOOLBAR_X - 8;
        int cursor = context.x(Math.min(titleRight, Math.max(8, rightLimit - textWidth)));
        int y = context.y(8);
        cursor += context.drawAbsolute(formedLabel, cursor, y, LABEL_COLOR);
        cursor += context.drawAbsolute(formedValue, cursor, y, state.formed() ? SUCCESS_COLOR : ERROR_COLOR);
        cursor += context.drawAbsolute(activeLabel, cursor, y, LABEL_COLOR);
        cursor += context.drawAbsolute(activeValue, cursor, y, state.active() ? SUCCESS_COLOR : MUTED_COLOR);
        cursor += context.drawAbsolute(networkLabel, cursor, y, LABEL_COLOR);
        context.drawAbsolute(networkValue, cursor, y, networkColor(state));
    }

    private static Component boolText(boolean value) {
        return Component.translatable(value ? "gui.neoecoae.common.yes" : "gui.neoecoae.common.no");
    }

    private static Component networkText(NECraftingUiState state) {
        if (state.networkMemberCount() <= 1) {
            return Component.translatable("gui.neoecoae.host.network.mode.local");
        }
        if (!state.networkConnected()) {
            return Component.translatable("gui.neoecoae.host.network.disconnected");
        }
        String key = state.networkMultiplier() >= 8
                ? "gui.neoecoae.host.network.mode.high_energy"
                : "gui.neoecoae.host.network.mode.normal";
        return Component.translatable(key);
    }

    private static int networkColor(NECraftingUiState state) {
        if (state.networkMemberCount() <= 1) {
            return MUTED_COLOR;
        }
        return state.networkConnected() ? SUCCESS_COLOR : ERROR_COLOR;
    }
}
