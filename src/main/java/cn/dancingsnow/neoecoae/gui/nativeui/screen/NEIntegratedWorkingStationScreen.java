package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.NENineSliceRenderer;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NETexturedButton;
import cn.dancingsnow.neoecoae.network.NENetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Screen for the ECO Integrated Working Station — 1.21.1-style layout.
 * <p>
 * Layout: 168×168 main panel with input/output/upgrade slots,
 * left/right fluid bars, progress bar, and left-side auto-IO button.
 * Slot background coordinates are 18×18 visual rectangles; the Menu uses
 * the corresponding +1/+1 coordinates for the 16×16 item/click area.
 * </p>
 */
public class NEIntegratedWorkingStationScreen extends AbstractContainerScreen<NEIntegratedWorkingStationMenu> {

    private static final ResourceLocation TEX_BG = NeoECOAE.id("textures/gui/background.png");
    private static final ResourceLocation TEX_SLOT = NeoECOAE.id("textures/gui/slot.png");
    private static final ResourceLocation TEX_INV_BORDER = NeoECOAE.id("textures/gui/inventory_border.png");
    private static final ResourceLocation TEX_BAR_CONTAINER = NeoECOAE.id("textures/gui/bar_container.png");

    private static final int PANEL_W = 168;
    private static final int PANEL_H = 168;
    private static final int GUI_WIDTH = 168;
    private static final int GUI_HEIGHT = 168;
    private static final int SLOT_SIZE = 18;
    private static final int ITEM_OFFSET = 1;

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

    // Output fluid bar (18×54) — same Y and H as input for alignment
    private static final int FLUID_OUT_X = 147;
    private static final int FLUID_OUT_Y = 14;
    private static final int FLUID_OUT_W = 18;
    private static final int FLUID_OUT_H = 54;

    // Machine slots: *_BG_* are 18×18 slot.png positions.
    // NEIntegratedWorkingStationMenu uses *_BG_* + ITEM_OFFSET for Slot positions.
    private static final int INPUT_COLS = 3;
    private static final int INPUT_ROWS = 3;
    private static final int INPUT_BG_X = 39;
    private static final int INPUT_BG_Y = 14;

    private static final int OUTPUT_BG_X = 108;
    private static final int OUTPUT_BG_Y = 32;

    // Right upgrade bar — fully covers 4 slots (18×4=72) + 2-3px margin each side
    private static final int UPGRADE_BAR_X = 170;
    private static final int UPGRADE_BAR_Y = 0;
    private static final int UPGRADE_BAR_W = 22;
    private static final int UPGRADE_BAR_H = 78;

    private static final int UPGRADE_BG_X = 171;
    private static final int UPGRADE_FIRST_BG_Y = 2;
    private static final int UPGRADE_COUNT = 4;

    // Player inventory groups
    private static final int INV_BORDER_X = 2;
    private static final int INV_BORDER_Y = 87;
    private static final int INV_BORDER_W = 165;
    private static final int INV_BORDER_H = 56;
    private static final int INV_BG_X = 3;
    private static final int INV_BG_Y = 88;

    private static final int HOTBAR_BORDER_X = 2;
    private static final int HOTBAR_BORDER_Y = 146;
    private static final int HOTBAR_BORDER_W = 165;
    private static final int HOTBAR_BORDER_H = 21;
    private static final int HOTBAR_BG_X = 3;
    private static final int HOTBAR_BG_Y = 148;

    // Colors (light panel style)
    private static final int TXT_PRIMARY = 0xFF403E53;
    private static final int TXT_HINT = 0xFF2F5F8F;
    private static final int FLUID_EMPTY = 0xFFADB0C4;
    private static final int FLUID_BORDER = 0xFF403E53;
    private static final int FLUID_IN_COLOR = 0xFF3A7FD6;
    private static final int FLUID_OUT_COLOR = 0xFF8E7CFF;

    // Left settings panel (matches 1.21.1 LDLib2 settingsPanel)
    private static final int SETTINGS_PANEL_X = -22;
    private static final int SETTINGS_PANEL_Y = 0;
    private static final int SETTINGS_PANEL_W = 22;
    private static final int SETTINGS_PANEL_H = 70;

    // Settings panel button positions (within panel, relative to leftPos/topPos)
    private static final int HELP_BTN_X = -21;
    private static final int HELP_BTN_Y = 1;
    private static final int HELP_BTN_W = 18;
    private static final int HELP_BTN_H = 20;

    private static final int TOGGLE_BTN_X = -21;
    private static final int TOGGLE_BTN_Y = 23;
    private static final int TOGGLE_BTN_W = 18;
    private static final int TOGGLE_BTN_H = 22;

    private static final int OUTPUTS_BTN_X = -21;
    private static final int OUTPUTS_BTN_Y = 47;
    private static final int OUTPUTS_BTN_W = 18;
    private static final int OUTPUTS_BTN_H = 20;

    // X-button constants (8x8 matches 1.21.1 LDLib2 reference)
    private static final int CLEAR_BTN_W = 8;
    private static final int CLEAR_BTN_H = 8;
    private static final int CLEAR_BTN_TEXT = 0xFFFFFFFF;
    private static final int CLEAR_BTN_DISABLED = 0xFF888888;
    private static final int CLEAR_BTN_IN_X = 20;
    private static final int CLEAR_BTN_OUT_X = 137;
    private static final int CLEAR_BTN_Y = 59;

    // Upgrade ghost placeholder (16x16 item area, alpha overlay)
    private static final float UPGRADE_GHOST_ALPHA = 0.30f;

    private NETexturedButton autoExportBtn;
    private NETexturedButton helpBtn;
    private NETexturedButton outputsBtn;

    public NEIntegratedWorkingStationScreen(NEIntegratedWorkingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        // ── Left settings panel: 3 vertical buttons ──
        // Help button (disabled, visual placeholder)
        helpBtn = new NETexturedButton(
            leftPos + HELP_BTN_X, topPos + HELP_BTN_Y, HELP_BTN_W, HELP_BTN_H,
            Component.literal("?"), btn -> {});
        helpBtn.active = false;
        helpBtn.setTooltip(Tooltip.create(
            Component.translatable("gui.neoecoae.integrated_working_station.help")));
        addRenderableWidget(helpBtn);

        // Auto-export toggle (functional)
        autoExportBtn = new NETexturedButton(
            leftPos + TOGGLE_BTN_X, topPos + TOGGLE_BTN_Y, TOGGLE_BTN_W, TOGGLE_BTN_H,
            Component.literal("\u2192"), btn -> sendAction(NENetwork.IWSAction.TOGGLE_AUTO_EXPORT));
        addRenderableWidget(autoExportBtn);

        // Outputs button (disabled, visual placeholder)
        outputsBtn = new NETexturedButton(
            leftPos + OUTPUTS_BTN_X, topPos + OUTPUTS_BTN_Y, OUTPUTS_BTN_W, OUTPUTS_BTN_H,
            Component.literal("\u25A5"), btn -> {});
        outputsBtn.active = false;
        outputsBtn.setTooltip(Tooltip.create(
            Component.translatable("gui.neoecoae.integrated_working_station.outputs")));
        addRenderableWidget(outputsBtn);

        // ── Fluid clear buttons (8x8, lowercase x) ──
        addRenderableWidget(new NETexturedButton(
            leftPos + CLEAR_BTN_IN_X, topPos + CLEAR_BTN_Y, CLEAR_BTN_W, CLEAR_BTN_H,
            Component.literal("x"),
            btn -> sendAction(NENetwork.IWSAction.CLEAR_INPUT_FLUID),
            CLEAR_BTN_TEXT, CLEAR_BTN_DISABLED, false));

        addRenderableWidget(new NETexturedButton(
            leftPos + CLEAR_BTN_OUT_X, topPos + CLEAR_BTN_Y, CLEAR_BTN_W, CLEAR_BTN_H,
            Component.literal("x"),
            btn -> sendAction(NENetwork.IWSAction.CLEAR_OUTPUT_FLUID),
            CLEAR_BTN_TEXT, CLEAR_BTN_DISABLED, false));
    }

    private void sendAction(NENetwork.IWSAction action) {
        NENetwork.CHANNEL.sendToServer(
            new NENetwork.NEIntegratedWorkingStationActionPacket(menu.getMachinePos(), action));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Update auto-export toggle state
        if (autoExportBtn != null) {
            boolean on = menu.isAutoExportEnabled();
            autoExportBtn.setMessage(Component.literal(on ? "\u2192" : "\u2190"));
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
        int uy = topPos + UPGRADE_BAR_Y;
        if (mouseX >= ux && mouseX < ux + UPGRADE_BAR_W && mouseY >= uy && mouseY < uy + UPGRADE_BAR_H) {
            g.renderTooltip(font, List.of(
                Component.translatable("gui.neoecoae.integrated_working_station.available_upgrades").getVisualOrderText(),
                Component.translatable("gui.neoecoae.integrated_working_station.speed_card_upgrade", 4).getVisualOrderText()
            ), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // ── 0. Left settings panel background ──
        NENineSliceRenderer.drawPanel(g, TEX_BG,
            leftPos + SETTINGS_PANEL_X, topPos + SETTINGS_PANEL_Y,
            SETTINGS_PANEL_W, SETTINGS_PANEL_H, 16, 16, 2, 2, 2, 4);

        // ── 1. Main panel (168×168) ──
        NENineSliceRenderer.drawPanel(g, TEX_BG, leftPos, topPos, PANEL_W, PANEL_H,
            16, 16, 2, 2, 2, 4);

        // ── 2. Right upgrade bar background ──
        NENineSliceRenderer.drawPanel(g, TEX_BG, leftPos + UPGRADE_BAR_X, topPos + UPGRADE_BAR_Y,
            UPGRADE_BAR_W, UPGRADE_BAR_H, 16, 16, 2, 2, 2, 4);

        // ── 3. Inventory borders ──
        NENineSliceRenderer.drawPanel(g, TEX_INV_BORDER,
            leftPos + INV_BORDER_X, topPos + INV_BORDER_Y,
            INV_BORDER_W, INV_BORDER_H, 16, 16, 1, 1, 1, 1);
        NENineSliceRenderer.drawPanel(g, TEX_INV_BORDER,
            leftPos + HOTBAR_BORDER_X, topPos + HOTBAR_BORDER_Y,
            HOTBAR_BORDER_W, HOTBAR_BORDER_H, 16, 16, 1, 1, 1, 1);

        // ── 4. Slot backgrounds ──
        for (int row = 0; row < INPUT_ROWS; row++) {
            for (int col = 0; col < INPUT_COLS; col++) {
                drawSlot(g, leftPos + INPUT_BG_X + col * SLOT_SIZE, topPos + INPUT_BG_Y + row * SLOT_SIZE);
            }
        }

        drawSlot(g, leftPos + OUTPUT_BG_X, topPos + OUTPUT_BG_Y);

        for (int i = 0; i < UPGRADE_COUNT; i++) {
            drawSlot(g, leftPos + UPGRADE_BG_X, topPos + UPGRADE_FIRST_BG_Y + i * SLOT_SIZE);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, leftPos + INV_BG_X + col * SLOT_SIZE, topPos + INV_BG_Y + row * SLOT_SIZE);
            }
        }

        for (int col = 0; col < 9; col++) {
            drawSlot(g, leftPos + HOTBAR_BG_X + col * SLOT_SIZE, topPos + HOTBAR_BG_Y);
        }

        // ── 5. Fluid bars (explicit w/h for alignment) ──
        drawFluidBar(g, leftPos + FLUID_IN_X, topPos + FLUID_IN_Y,
            FLUID_IN_W, FLUID_IN_H, menu.getFluidInAmount(), FLUID_IN_COLOR);
        drawFluidBar(g, leftPos + FLUID_OUT_X, topPos + FLUID_OUT_Y,
            FLUID_OUT_W, FLUID_OUT_H, menu.getFluidOutAmount(), FLUID_OUT_COLOR);

        // ── 6. Progress bar (6×18, bottom-up with textures) ──
        drawProgressBar(g, leftPos + PROGRESS_X, topPos + PROGRESS_Y, menu.getProgress(), menu.getMaxProgress());

        // ── 7. Upgrade ghost placeholders (empty slots only, 16x16 item area) ──
        int startUpgradeSlot = NEIntegratedWorkingStationMenu.INPUT_SLOTS
            + NEIntegratedWorkingStationMenu.OUTPUT_SLOTS;
        RenderSystem.enableBlend();
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            int slotIdx = startUpgradeSlot + i;
            if (slotIdx < menu.slots.size() && !menu.getSlot(slotIdx).hasItem()) {
                int gx = leftPos + UPGRADE_BG_X + ITEM_OFFSET;
                int gy = topPos + UPGRADE_FIRST_BG_Y + ITEM_OFFSET + i * SLOT_SIZE;
                RenderSystem.setShaderColor(1, 1, 1, UPGRADE_GHOST_ALPHA);
                g.fill(gx, gy, gx + 16, gy + 16, 0x60FFFFFF);
                g.fill(gx + 2, gy + 2, gx + 14, gy + 14, 0x40707070);
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
        }
        RenderSystem.disableBlend();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 5, TXT_PRIMARY, false);
        g.drawString(font, Component.translatable("gui.neoecoae.common.inventory"), 3, 75, TXT_HINT, false);
    }

    // ── Drawing helpers ──

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.blit(TEX_SLOT, x, y, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
    }

    private void drawFluidBar(GuiGraphics g, int x, int y, int w, int h, int amount, int color) {
        g.fill(x, y, x + w, y + h, FLUID_EMPTY);
        g.fill(x, y, x + w, y + 1, FLUID_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, FLUID_BORDER);
        g.fill(x, y, x + 1, y + h, FLUID_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, FLUID_BORDER);
        if (amount > 0) {
            int barH = Math.max(1, amount * (h - 2) / 16000);
            g.fill(x + 1, y + h - 1 - barH, x + w - 1, y + h - 1, color);
        }
    }

    private void drawProgressBar(GuiGraphics g, int x, int y, int progress, int max) {
        // ── Container background (6×18) ──
        RenderSystem.setShaderTexture(0, TEX_BAR_CONTAINER);
        g.blit(TEX_BAR_CONTAINER, x, y, 0, 0, PROGRESS_W, PROGRESS_H, PROGRESS_W, PROGRESS_H);

        // ── Fill bar (4×16 inner, bottom-up, solid purple — guaranteed visible) ──
        if (max > 0 && progress > 0) {
            int innerX = x + ITEM_OFFSET;
            int innerY = y + ITEM_OFFSET;
            int innerW = PROGRESS_W - ITEM_OFFSET * 2;  // 4
            int innerH = PROGRESS_H - ITEM_OFFSET * 2;  // 16
            int h = Mth.clamp(progress * innerH / max, 1, innerH);
            g.fill(innerX, innerY + innerH - h, innerX + innerW, innerY + innerH, 0xFF5A49D6);
        }
    }
}