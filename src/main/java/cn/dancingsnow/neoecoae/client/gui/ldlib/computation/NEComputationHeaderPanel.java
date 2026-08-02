package cn.dancingsnow.neoecoae.client.gui.ldlib.computation;

import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.*;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Two-line title/network status and CPU-selection tooltip for the computation host. */
public final class NEComputationHeaderPanel {
    public void draw(
            GuiGraphics g,
            Font font,
            Component title,
            NEComputationUiState state,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY) {
        int titleX = screenX.applyAsInt(HEADER_TITLE_X);
        int titleY = screenY.applyAsInt(HEADER_TITLE_Y);
        int titleRight = screenX.applyAsInt(HEADER_STATUS_RIGHT);
        g.enableScissor(titleX, titleY - 1, titleRight, titleY + font.lineHeight + 1);
        g.drawString(
                font,
                font.plainSubstrByWidth(title.getString(), HEADER_STATUS_RIGHT - HEADER_TITLE_X),
                titleX,
                titleY,
                0xFF4A4A4A,
                false);
        g.disableScissor();

        int statusY = screenY.applyAsInt(HEADER_STATUS_Y);
        int modeX = screenX.applyAsInt(HEADER_TITLE_X);
        drawFitted(g, font, networkModeText(state), modeX, statusY, HEADER_MODE_W, networkModeColor(state));
        int connectionX = screenX.applyAsInt(HEADER_TITLE_X + HEADER_MODE_W + HEADER_GAP);
        drawFitted(
                g,
                font,
                networkConnectionText(state),
                connectionX,
                statusY,
                HEADER_CONNECTION_W,
                state.networkConnected() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR);
    }

    public boolean drawTooltip(
            GuiGraphics g,
            Font font,
            NEComputationUiState state,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            int mouseX,
            int mouseY) {
        if (!Widget.isMouseOver(
                screenX.applyAsInt(CPU_BUTTON_X),
                screenY.applyAsInt(CPU_BUTTON_Y),
                CPU_BUTTON_W,
                CPU_BUTTON_H,
                mouseX,
                mouseY)) {
            return false;
        }
        g.renderTooltip(
                font,
                List.of(
                        Component.translatable("gui.neoecoae.computation.cpu_selection_mode"),
                        cpuModeTooltip(state.cpuSelectionMode()),
                        Component.translatable("gui.neoecoae.computation.cpu_selection_mode.click")),
                Optional.empty(),
                mouseX,
                mouseY);
        return true;
    }

    private static Component cpuModeTooltip(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.player_only");
            case MACHINE_ONLY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.machine_only");
            case ANY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.any");
        };
    }

    private static Component networkModeText(NEComputationUiState state) {
        String key = state.networkMultiplier() >= 8
                ? "gui.neoecoae.host.network.mode.high_energy"
                : state.networkMultiplier() >= 2
                        ? "gui.neoecoae.host.network.mode.normal"
                        : "gui.neoecoae.host.network.mode.local";
        return Component.translatable(key);
    }

    private static Component networkConnectionText(NEComputationUiState state) {
        return Component.translatable(
                state.networkConnected()
                        ? "gui.neoecoae.host.network.connected"
                        : "gui.neoecoae.host.network.disconnected");
    }

    private static int networkModeColor(NEComputationUiState state) {
        return state.networkMultiplier() <= 1 ? NELDLibStyle.DARK_TEXT_MUTED : NELDLibStyle.DARK_TEXT_SUCCESS;
    }

    private static void drawFitted(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        g.enableScissor(x, y - 1, x + maxWidth, y + font.lineHeight + 1);
        g.drawString(font, font.plainSubstrByWidth(text.getString(), maxWidth), x, y, color, false);
        g.disableScissor();
    }
}
