package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.NENineSliceRenderer;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NETexturedButton;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the ECO Integrated Working Station.
 * <p>
 * Layout: 176×196 with 3×3 input, 1 output, player inventory, progress bar,
 * fluid bar, and side action buttons.
 * </p>
 */
public class NEIntegratedWorkingStationScreen extends AbstractContainerScreen<NEIntegratedWorkingStationMenu> {

    private static final ResourceLocation TEX_BG = NeoECOAE.id("textures/gui/background.png");
    private static final ResourceLocation TEX_SLOT = NeoECOAE.id("textures/gui/slot.png");
    private static final int TEX_SIZE = 16;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 196;

    // Progress bar
    private static final int PROGRESS_X = 150;
    private static final int PROGRESS_Y = 17;
    private static final int PROGRESS_W = 10;
    private static final int PROGRESS_H = 54;

    // Fluid bar
    private static final int FLUID_X = 8;
    private static final int FLUID_Y = 17;
    private static final int FLUID_W = 16;
    private static final int FLUID_H = 54;

    // IWS text colors (white on dark panel)
    private static final int TEXT_COLOR = 0xFFE8E8F0;
    private static final int TEXT_VALUE_COLOR = 0xFF8E7CFF;
    private static final int TEXT_SUCCESS_COLOR = 0xFF33FF66;
    private static final int TEXT_HINT_COLOR = 0xFF7FA6D8;

    // Border colors
    private static final int BORDER_DARK = 0xFF373737;
    private static final int BORDER_LIGHT = 0xFF8B8B8B;
    private static final int BAR_BG = 0xFF1E1E2A;

    public NEIntegratedWorkingStationScreen(NEIntegratedWorkingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        // ── Right-side action buttons ──
        int btnX = leftPos + 176;
        int btnY = topPos + 4;

        // Auto-export toggle
        addRenderableWidget(new NETexturedButton(btnX, btnY, 20, 20,
            Component.literal("A"),
            btn -> sendAction(NENetwork.IWSAction.TOGGLE_AUTO_EXPORT)));
        btnY += 22;

        // Clear input fluid
        addRenderableWidget(new NETexturedButton(btnX, btnY, 20, 20,
            Component.literal("I"),
            btn -> sendAction(NENetwork.IWSAction.CLEAR_INPUT_FLUID)));
        btnY += 22;

        // Clear output fluid
        addRenderableWidget(new NETexturedButton(btnX, btnY, 20, 20,
            Component.literal("O"),
            btn -> sendAction(NENetwork.IWSAction.CLEAR_OUTPUT_FLUID)));
    }

    private void sendAction(NENetwork.IWSAction action) {
        NENetwork.CHANNEL.sendToServer(
            new NENetwork.NEIntegratedWorkingStationActionPacket(menu.getMachinePos(), action));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Nine-slice background panel
        NENineSliceRenderer.drawPanel(g, TEX_BG, leftPos, topPos, imageWidth, imageHeight,
            TEX_SIZE, TEX_SIZE, 2, 2, 2, 4);

        // ── Draw all slot backgrounds ──
        // 3×3 input slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlot(g, leftPos + 30 + col * 18, topPos + 17 + row * 18);
            }
        }
        // Output slot
        drawSlot(g, leftPos + 124, topPos + 35);

        // Player inventory 3×9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, leftPos + 8 + col * 18, topPos + 114 + row * 18);
            }
        }
        // Player hotbar 1×9
        for (int col = 0; col < 9; col++) {
            drawSlot(g, leftPos + 8 + col * 18, topPos + 172);
        }

        // ── Fluid bar border + fill ──
        int fluidAmount = menu.getFluidInAmount();
        drawBarBorder(g, leftPos + FLUID_X, topPos + FLUID_Y, FLUID_W, FLUID_H);
        if (fluidAmount > 0) {
            int fluidH = (int) ((float) fluidAmount / 16000 * FLUID_H);
            int fluidBarY = topPos + FLUID_Y + FLUID_H - fluidH;
            g.fill(leftPos + FLUID_X + 1, fluidBarY,
                leftPos + FLUID_X + FLUID_W - 1, topPos + FLUID_Y + FLUID_H - 1,
                0xFF3A7FD6);
        }

        // ── Progress bar border + fill ──
        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        drawBarBorder(g, leftPos + PROGRESS_X, topPos + PROGRESS_Y, PROGRESS_W, PROGRESS_H);
        if (maxProgress > 0 && progress > 0) {
            int barHeight = Math.max(1, (int) ((float) progress / maxProgress * (PROGRESS_H - 2)));
            int barY = topPos + PROGRESS_Y + PROGRESS_H - 1 - barHeight;
            g.fill(leftPos + PROGRESS_X + 1, barY,
                leftPos + PROGRESS_X + PROGRESS_W - 1, topPos + PROGRESS_Y + PROGRESS_H - 1,
                0xFF5A49D6);
        }
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.blit(TEX_SLOT, x, y, 0, 0, 18, 18, 18, 18);
    }

    private void drawBarBorder(GuiGraphics g, int x, int y, int w, int h) {
        // Background fill
        g.fill(x, y, x + w, y + h, BAR_BG);
        // Border (1px dark)
        g.fill(x, y, x + w, y + 1, BORDER_DARK);
        g.fill(x, y + h - 1, x + w, y + h, BORDER_DARK);
        g.fill(x, y, x + 1, y + h, BORDER_DARK);
        g.fill(x + w - 1, y, x + w, y + h, BORDER_DARK);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Title
        drawShadowed(g, title, 8, 6, TEXT_COLOR);

        // Inventory label
        drawShadowed(g, Component.translatable("gui.neoecoae.common.inventory"),
            8, 102, TEXT_HINT_COLOR);

        // Energy text
        int energy = menu.getEnergy();
        int requiredEnergy = menu.getRequiredEnergy();
        Component energyText;
        if (requiredEnergy > 0) {
            energyText = Component.translatable("gui.neoecoae.integrated_working_station.energy",
                formatEnergy(requiredEnergy));
        } else {
            energyText = Component.translatable("gui.neoecoae.integrated_working_station.energy",
                formatEnergy(energy));
        }
        drawShadowed(g, energyText, 8, 78, TEXT_COLOR);

        // Progress percentage
        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        if (maxProgress > 0) {
            int pct = progress * 100 / maxProgress;
            drawShadowed(g, Component.literal(pct + "%"),
                PROGRESS_X, PROGRESS_Y + PROGRESS_H + 4, TEXT_VALUE_COLOR);
        }

        // Working status
        if (menu.isWorking()) {
            drawShadowed(g, Component.translatable("gui.neoecoae.machine.active"),
                90, 78, TEXT_SUCCESS_COLOR);
        }
    }

    // ── Drawing helpers ──

    /** Draw text with black shadow (white text on dark IWS panel). */
    private void drawShadowed(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, x + 1, y + 1, 0xFF000000);
        g.drawString(font, text, x, y, color);
    }

    private static String formatEnergy(int ae) {
        if (ae >= 1000) {
            return (ae / 1000) + "k FE";
        }
        return ae + " FE";
    }
}
