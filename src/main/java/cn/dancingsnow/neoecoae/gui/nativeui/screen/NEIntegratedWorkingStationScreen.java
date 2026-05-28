package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.NENineSliceRenderer;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NETexturedButton;
import cn.dancingsnow.neoecoae.network.NENetwork;
import appeng.core.definitions.AEItems;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Screen for the ECO Integrated Working Station — 1.21.1-style layout.
 * <p>
 * Layout: 204×196 with input/output/upgrade slots, left/right fluid bars,
 * progress bar, and left-side action buttons.
 * </p>
 */
public class NEIntegratedWorkingStationScreen extends AbstractContainerScreen<NEIntegratedWorkingStationMenu> {

    private static final ResourceLocation TEX_BG = NeoECOAE.id("textures/gui/background.png");
    private static final ResourceLocation TEX_SLOT = NeoECOAE.id("textures/gui/slot.png");
    private static final ResourceLocation TEX_INV_BORDER = NeoECOAE.id("textures/gui/inventory_border.png");
    private static final ResourceLocation TEX_BAR_CONTAINER = NeoECOAE.id("textures/gui/bar_container.png");
    private static final ResourceLocation TEX_BAR = NeoECOAE.id("textures/gui/bar.png");

    private static final int PANEL_W = 168;
    private static final int PANEL_H = 166;
    private static final int GUI_WIDTH = 168;
    private static final int GUI_HEIGHT = 166;

    // Progress bar (6×18 vertical)
    private static final int PROGRESS_X = 128;
    private static final int PROGRESS_Y = 32;
    private static final int PROGRESS_W = 6;
    private static final int PROGRESS_H = 18;

    // Input fluid bar (18×54)
    private static final int FLUID_IN_X = 3;
    private static final int FLUID_IN_Y = 14;
    private static final int FLUID_IN_W = 18;
    private static final int FLUID_IN_H = 54;

    // Output fluid bar (18×54)
    private static final int FLUID_OUT_X = 147;
    private static final int FLUID_OUT_Y = 14;
    private static final int FLUID_OUT_W = 18;
    private static final int FLUID_OUT_H = 54;

    // Upgrade slots (right bar, 20×74)
    private static final int UPGRADE_BAR_X = 170;
    private static final int UPGRADE_X = 172;
    private static final int UPGRADE_FIRST_Y = 3;
    private static final int UPGRADE_SPACING = 18;

    // Colors (light panel style)
    private static final int TXT_PRIMARY = 0xFF403E53;
    private static final int TXT_VALUE = 0xFF5A49D6;
    private static final int TXT_HINT = 0xFF2F5F8F;
    private static final int FLUID_EMPTY = 0xFFADB0C4;
    private static final int FLUID_BORDER = 0xFF403E53;
    private static final int FLUID_IN_COLOR = 0xFF3A7FD6;
    private static final int FLUID_OUT_COLOR = 0xFF8E7CFF;

    private NETexturedButton autoExportBtn;
    private final ItemStack ghostSpeedCard;

    public NEIntegratedWorkingStationScreen(NEIntegratedWorkingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.ghostSpeedCard = AEItems.SPEED_CARD.stack();
    }

    @Override
    protected void init() {
        super.init();

        // ── Left-side action buttons (at -21, 22/43) ──
        // Auto IO toggle (active)
        autoExportBtn = new NETexturedButton(leftPos - 21, topPos + 22, 18, 20,
            Component.literal("IO"),
            btn -> sendAction(NENetwork.IWSAction.TOGGLE_AUTO_EXPORT));
        addRenderableWidget(autoExportBtn);

        // Allow output sides placeholder (disabled)
        var allowOutBtn = new NETexturedButton(leftPos - 21, topPos + 43, 18, 20,
            Component.literal("S"), btn -> {});
        allowOutBtn.active = false;
        addRenderableWidget(allowOutBtn);

        // ── Fluid clear X buttons ──
        // Clear input fluid at (21, 60)
        addRenderableWidget(new NETexturedButton(
            leftPos + 21, topPos + 60, 8, 8, Component.literal("X"),
            btn -> sendAction(NENetwork.IWSAction.CLEAR_INPUT_FLUID)));

        // Clear output fluid at (139, 60)
        addRenderableWidget(new NETexturedButton(
            leftPos + 139, topPos + 60, 8, 8, Component.literal("X"),
            btn -> sendAction(NENetwork.IWSAction.CLEAR_OUTPUT_FLUID)));
    }

    private void sendAction(NENetwork.IWSAction action) {
        NENetwork.CHANNEL.sendToServer(
            new NENetwork.NEIntegratedWorkingStationActionPacket(menu.getMachinePos(), action));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Update auto-export button state
        if (autoExportBtn != null) {
            boolean on = menu.isAutoExportEnabled();
            autoExportBtn.setMessage(Component.literal(on ? "IO+" : "IO-"));
            autoExportBtn.setTooltip(Tooltip.create(
                Component.translatable(on
                    ? "gui.neoecoae.integrated_working_station.auto_io.on"
                    : "gui.neoecoae.integrated_working_station.auto_io.off")));
        }
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);

        // ── Progress bar tooltip ──
        int px = leftPos + PROGRESS_X;
        int py = topPos + PROGRESS_Y;
        if (mouseX >= px && mouseX < px + PROGRESS_W && mouseY >= py && mouseY < py + PROGRESS_H) {
            int progress = menu.getProgress();
            int maxProgress = menu.getMaxProgress();
            int pct = maxProgress > 0 ? progress * 100 / maxProgress : 0;
            g.renderTooltip(font, List.of(
                Component.translatable("gui.neoecoae.integrated_working_station.work_progress",
                    progress, maxProgress).getVisualOrderText(),
                Component.translatable("gui.neoecoae.integrated_working_station.progress_percent", pct).getVisualOrderText()
            ), mouseX, mouseY);
        }

        // ── Upgrade area tooltip ──
        int ux = leftPos + UPGRADE_BAR_X;
        int uy = topPos;
        if (mouseX >= ux && mouseX < ux + 20 && mouseY >= uy && mouseY < uy + 74) {
            g.renderTooltip(font, List.of(
                Component.translatable("gui.neoecoae.integrated_working_station.available_upgrades").getVisualOrderText(),
                Component.translatable("gui.neoecoae.integrated_working_station.speed_card_upgrade", 4).getVisualOrderText()
            ), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Main panel (168×166)
        NENineSliceRenderer.drawPanel(g, TEX_BG, leftPos, topPos, PANEL_W, PANEL_H,
            16, 16, 2, 2, 2, 4);
        // Right upgrade bar (20×74)
        NENineSliceRenderer.drawPanel(g, TEX_BG, leftPos + UPGRADE_BAR_X, topPos, 20, 74,
            16, 16, 2, 2, 2, 4);

        // Inventory borders
        NENineSliceRenderer.drawPanel(g, TEX_INV_BORDER, leftPos + 2, topPos + 87, 164, 56, 16, 16, 1, 1, 1, 1);
        NENineSliceRenderer.drawPanel(g, TEX_INV_BORDER, leftPos + 2, topPos + 147, 164, 20, 16, 16, 1, 1, 1, 1);

        // ── Slot backgrounds ──
        // 3×3 input at (39,14)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++)
                drawSlot(g, leftPos + 39 + col * 18, topPos + 14 + row * 18);
        // Output at (108,32)
        drawSlot(g, leftPos + 108, topPos + 32);
        // Upgrades at (172, 3+18*i)
        for (int i = 0; i < 4; i++)
            drawSlot(g, leftPos + UPGRADE_X, topPos + UPGRADE_FIRST_Y + i * UPGRADE_SPACING);
        // Player inventory 3×9 at (3,88)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlot(g, leftPos + 3 + col * 18, topPos + 88 + row * 18);
        // Player hotbar 1×9 at (3,148)
        for (int col = 0; col < 9; col++)
            drawSlot(g, leftPos + 3 + col * 18, topPos + 148);

        // Ghost speed card in empty upgrade slots
        for (int i = 0; i < 4; i++) {
            if (menu.slots.size() > 10 + i && !menu.getSlot(10 + i).hasItem()) {
                PoseStack ps = g.pose();
                ps.pushPose();
                ps.translate(leftPos + UPGRADE_X + 1, topPos + UPGRADE_FIRST_Y + i * UPGRADE_SPACING + 1, 0);
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1, 1, 1, 0.3f);
                g.renderItem(ghostSpeedCard, 0, 0);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.disableBlend();
                ps.popPose();
            }
        }

        // ── Fluid bars ──
        drawFluidBar(g, leftPos + FLUID_IN_X, topPos + FLUID_IN_Y, menu.getFluidInAmount(), FLUID_IN_COLOR);
        drawFluidBar(g, leftPos + FLUID_OUT_X, topPos + FLUID_OUT_Y, menu.getFluidOutAmount(), FLUID_OUT_COLOR);

        // ── Progress bar (6×18, bottom-up with textures) ──
        drawProgressBar(g, leftPos + PROGRESS_X, topPos + PROGRESS_Y, menu.getProgress(), menu.getMaxProgress());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 5, TXT_PRIMARY, false);
        g.drawString(font, Component.translatable("gui.neoecoae.common.inventory"), 3, 75, TXT_HINT, false);
    }

    // ── Drawing helpers ──

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.blit(TEX_SLOT, x, y, 0, 0, 18, 18, 18, 18);
    }

    private void drawFluidBar(GuiGraphics g, int x, int y, int amount, int color) {
        g.fill(x, y, x + FLUID_IN_W, y + FLUID_IN_H, FLUID_EMPTY);
        g.fill(x, y, x + FLUID_IN_W, y + 1, FLUID_BORDER);
        g.fill(x, y + FLUID_IN_H - 1, x + FLUID_IN_W, y + FLUID_IN_H, FLUID_BORDER);
        g.fill(x, y, x + 1, y + FLUID_IN_H, FLUID_BORDER);
        g.fill(x + FLUID_IN_W - 1, y, x + FLUID_IN_W, y + FLUID_IN_H, FLUID_BORDER);
        if (amount > 0) {
            int h = Math.max(1, amount * (FLUID_IN_H - 2) / 16000);
            g.fill(x + 1, y + FLUID_IN_H - 1 - h, x + FLUID_IN_W - 1, y + FLUID_IN_H - 1, color);
        }
    }

    private void drawProgressBar(GuiGraphics g, int x, int y, int progress, int max) {
        RenderSystem.setShaderTexture(0, TEX_BAR_CONTAINER);
        g.blit(TEX_BAR_CONTAINER, x, y, 0, 0, PROGRESS_W, PROGRESS_H, PROGRESS_W, PROGRESS_H);
        if (max > 0 && progress > 0) {
            int h = Math.min(16, Math.max(1, progress * 16 / max));
            RenderSystem.setShaderTexture(0, TEX_BAR);
            g.blit(TEX_BAR, x + 1, y + 1 + 16 - h, 0, 16 - h, 4, h, 4, 16);
        }
    }
}
