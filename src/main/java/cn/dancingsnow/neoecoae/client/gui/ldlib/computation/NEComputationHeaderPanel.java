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

/** Title, formed/active state, and CPU-selection tooltip for the computation host. */
public final class NEComputationHeaderPanel {
    public void draw(
            GuiGraphics g,
            Font font,
            Component title,
            NEComputationUiState state,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY) {
        Component formedLabel =
                Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component formedValue = boolText(state.formed());
        Component activeLabel = Component.literal("  ")
                .append(Component.translatable("gui.neoecoae.machine.active"))
                .append(": ");
        Component activeValue = boolText(state.active());
        int statusWidth =
                font.width(formedLabel) + font.width(formedValue) + font.width(activeLabel) + font.width(activeValue);
        int statusX = screenX.applyAsInt(HEADER_STATUS_RIGHT - statusWidth);
        int titleX = screenX.applyAsInt(8);
        int y = screenY.applyAsInt(8);

        g.enableScissor(titleX, y - 1, Math.max(titleX, statusX - 6), y + font.lineHeight + 1);
        g.drawString(font, title, titleX, y, 0xFF4A4A4A, false);
        g.disableScissor();
        g.drawString(font, formedLabel, statusX, y, 0xFF4A4A4A, false);
        int cursor = statusX + font.width(formedLabel);
        g.drawString(
                font,
                formedValue,
                cursor,
                y,
                state.formed() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR,
                false);
        cursor += font.width(formedValue);
        g.drawString(font, activeLabel, cursor, y, 0xFF4A4A4A, false);
        cursor += font.width(activeLabel);
        g.drawString(
                font,
                activeValue,
                cursor,
                y,
                state.active() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_MUTED,
                false);
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

    private static Component boolText(boolean value) {
        return Component.translatable(value ? "gui.neoecoae.common.yes" : "gui.neoecoae.common.no");
    }
}
