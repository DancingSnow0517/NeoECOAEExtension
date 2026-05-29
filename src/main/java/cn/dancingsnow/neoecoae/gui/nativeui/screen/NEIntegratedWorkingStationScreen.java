package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.NENineSliceRenderer;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NEClearFluidButton;
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
import net.minecraftforge.fluids.FluidStack;

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
    private static final int PANEL_H = 171;
    private static final int GUI_WIDTH = 168;
    private static final int GUI_HEIGHT = 171;
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

    // Right upgrade bar — pixel-aligned wrapper for 4 slots
    private static final int UPGRADE_BAR_X = 170;
    private static final int UPGRADE_BAR_Y = 1;
    private static final int UPGRADE_BAR_W = 22;
    private static final int UPGRADE_BAR_H = 76;

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

    // Left settings panel — single auto-export button only
    private static final int SETTINGS_PANEL_X = -20;
    private static final int SETTINGS_PANEL_Y = 1;
    private static final int SETTINGS_PANEL_W = 20;
    private static final int SETTINGS_PANEL_H = 24;

    private static final int TOGGLE_BTN_X = -19;
    private static final int TOGGLE_BTN_Y = 2;
    private static final int TOGGLE_BTN_W = 18;
    private static final int TOGGLE_BTN_H = 20;

    // X-button constants (8x8 matches 1.21.1 LDLib2 reference)
    private static final int CLEAR_BTN_W = 8;
    private static final int CLEAR_BTN_H = 8;
    private static final int CLEAR_BTN_TEXT = 0xFFFFFFFF;
    private static final int CLEAR_BTN_DISABLED = 0xFF888888;
    private static final int CLEAR_BTN_IN_X = 20;
    private static final int CLEAR_BTN_OUT_X = 137;
    private static final int CLEAR_BTN_Y = 59;

    // Upgrade blank-card placeholder colors (opaque)
    private static final int CARD_BORDER = 0xFF6F7288;
    private static final int CARD_FILL   = 0xFFB5B8C8;
    private static final int CARD_LINE   = 0xFF8E91A5;

    private NETexturedButton autoExportBtn;

    public NEIntegratedWorkingStationScreen(NEIntegratedWorkingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        // ── Left panel: single auto-export toggle button ──
        autoExportBtn = new NETexturedButton(
            leftPos + TOGGLE_BTN_X, topPos + TOGGLE_BTN_Y, TOGGLE_BTN_W, TOGGLE_BTN_H,
            Component.literal("\u2192"), btn -> sendAction(NENetwork.IWSAction.TOGGLE_AUTO_EXPORT));
        addRenderableWidget(autoExportBtn);

        // ── Fluid clear buttons (8x8, pixel-drawn x) ──
        addRenderableWidget(new NEClearFluidButton(
            leftPos + CLEAR_BTN_IN_X, topPos + CLEAR_BTN_Y,
            btn -> sendAction(NENetwork.IWSAction.CLEAR_INPUT_FLUID)));

        addRenderableWidget(new NEClearFluidButton(
            leftPos + CLEAR_BTN_OUT_X, topPos + CLEAR_BTN_Y,
            btn -> sendAction(NENetwork.IWSAction.CLEAR_OUTPUT_FLUID)));
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
            autoExportBtn.setMessage(Component.literal(on ? "\u2192" : "\u00D7"));
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

        // ── 5. Fluid bars with real fluid texture ──
        drawFluidStackBar(g, leftPos + FLUID_IN_X, topPos + FLUID_IN_Y,
            FLUID_IN_W, FLUID_IN_H, true, FLUID_IN_COLOR);
        drawFluidStackBar(g, leftPos + FLUID_OUT_X, topPos + FLUID_OUT_Y,
            FLUID_OUT_W, FLUID_OUT_H, false, FLUID_OUT_COLOR);

        // ── 6. Progress bar (6×18, bottom-up with textures) ──
        drawProgressBar(g, leftPos + PROGRESS_X, topPos + PROGRESS_Y, menu.getProgress(), menu.getMaxProgress());

        // ── 7. Upgrade blank-card placeholder (empty slots only, opaque, 16x16 item area) ──
        int startUpgradeSlot = NEIntegratedWorkingStationMenu.INPUT_SLOTS
            + NEIntegratedWorkingStationMenu.OUTPUT_SLOTS;
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            int slotIdx = startUpgradeSlot + i;
            if (slotIdx < menu.slots.size() && !menu.getSlot(slotIdx).hasItem()) {
                int gx = leftPos + UPGRADE_BG_X + ITEM_OFFSET;
                int gy = topPos + UPGRADE_FIRST_BG_Y + ITEM_OFFSET + i * SLOT_SIZE;
                drawBlankCard(g, gx, gy);
            }
        }

        // ── 8. Fluid hover overlays ──
        drawFluidHover(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 5, TXT_PRIMARY, false);
        g.drawString(font, Component.translatable("gui.neoecoae.common.inventory"), 3, 75, TXT_HINT, false);
    }

    // ── Drawing helpers ──

    // ── Blank card placeholder drawing ──

    private void drawBlankCard(GuiGraphics g, int gx, int gy) {
        // 12x12 card centred in 16x16 item area
        g.fill(gx + 2, gy + 2, gx + 14, gy + 14, CARD_FILL);
        g.fill(gx + 2, gy + 2, gx + 14, gy + 3, CARD_BORDER);
        g.fill(gx + 2, gy + 13, gx + 14, gy + 14, CARD_BORDER);
        g.fill(gx + 2, gy + 2, gx + 3, gy + 14, CARD_BORDER);
        g.fill(gx + 13, gy + 2, gx + 14, gy + 14, CARD_BORDER);
        g.fill(gx + 4, gy + 7, gx + 12, gy + 8, CARD_LINE);
    }

    // ── Fluid hover overlay ──

    private void drawFluidHover(GuiGraphics g, int mouseX, int mouseY) {
        int fiX = leftPos + FLUID_IN_X;
        int fiY = topPos + FLUID_IN_Y;
        if (mouseX >= fiX && mouseX < fiX + FLUID_IN_W && mouseY >= fiY && mouseY < fiY + FLUID_IN_H) {
            g.fill(fiX + 1, fiY + 1, fiX + FLUID_IN_W - 1, fiY + FLUID_IN_H - 1, 0x40FFFFFF);
            FluidStack inStack = menu.getClientInputFluid();
            if (!inStack.isEmpty()) {
                g.renderTooltip(font, List.of(
                    inStack.getDisplayName().getVisualOrderText(),
                    Component.literal(inStack.getAmount() + " / 16000 mB").getVisualOrderText()
                ), mouseX, mouseY);
            } else {
                g.renderTooltip(font, Component.literal(menu.getFluidInAmount() + " / 16000 mB"), mouseX, mouseY);
            }
        }
        int foX = leftPos + FLUID_OUT_X;
        int foY = topPos + FLUID_OUT_Y;
        if (mouseX >= foX && mouseX < foX + FLUID_OUT_W && mouseY >= foY && mouseY < foY + FLUID_OUT_H) {
            g.fill(foX + 1, foY + 1, foX + FLUID_OUT_W - 1, foY + FLUID_OUT_H - 1, 0x40FFFFFF);
            FluidStack outStack = menu.getClientOutputFluid();
            if (!outStack.isEmpty()) {
                g.renderTooltip(font, List.of(
                    outStack.getDisplayName().getVisualOrderText(),
                    Component.literal(outStack.getAmount() + " / 16000 mB").getVisualOrderText()
                ), mouseX, mouseY);
            } else {
                g.renderTooltip(font, Component.literal(menu.getFluidOutAmount() + " / 16000 mB"), mouseX, mouseY);
            }
        }
    }

    // ── Mouse click: auto export > clear buttons > fluid tank > super ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            // 1. Auto export toggle
            if (mx >= leftPos + TOGGLE_BTN_X && mx < leftPos + TOGGLE_BTN_X + TOGGLE_BTN_W
                && my >= topPos + TOGGLE_BTN_Y && my < topPos + TOGGLE_BTN_Y + TOGGLE_BTN_H) {
                sendAction(NENetwork.IWSAction.TOGGLE_AUTO_EXPORT);
                return true;
            }
            // 2. Input clear button
            if (mx >= leftPos + CLEAR_BTN_IN_X && mx < leftPos + CLEAR_BTN_IN_X + CLEAR_BTN_W
                && my >= topPos + CLEAR_BTN_Y && my < topPos + CLEAR_BTN_Y + CLEAR_BTN_H) {
                sendAction(NENetwork.IWSAction.CLEAR_INPUT_FLUID);
                return true;
            }
            // 3. Output clear button
            if (mx >= leftPos + CLEAR_BTN_OUT_X && mx < leftPos + CLEAR_BTN_OUT_X + CLEAR_BTN_W
                && my >= topPos + CLEAR_BTN_Y && my < topPos + CLEAR_BTN_Y + CLEAR_BTN_H) {
                sendAction(NENetwork.IWSAction.CLEAR_OUTPUT_FLUID);
                return true;
            }
            // 4. Input fluid tank container click
            if (mx >= leftPos + FLUID_IN_X && mx < leftPos + FLUID_IN_X + FLUID_IN_W
                && my >= topPos + FLUID_IN_Y && my < topPos + FLUID_IN_Y + FLUID_IN_H) {
                sendAction(NENetwork.IWSAction.INPUT_TANK_CONTAINER_CLICK);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

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

    private void drawFluidStackBar(GuiGraphics g, int x, int y, int w, int h,
                                    boolean input, int fallbackColor) {
        g.fill(x, y, x + w, y + h, FLUID_EMPTY);
        g.fill(x, y, x + w, y + 1, FLUID_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, FLUID_BORDER);
        g.fill(x, y, x + 1, y + h, FLUID_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, FLUID_BORDER);

        FluidStack stack = input ? menu.getClientInputFluid() : menu.getClientOutputFluid();
        int amount = stack.getAmount();
        if (amount <= 0) {
            amount = input ? menu.getFluidInAmount() : menu.getFluidOutAmount();
        }
        if (amount <= 0) return;

        int barH = Mth.clamp(amount * (h - 2) / 16000, 1, h - 2);
        int fillX = x + 1;
        int fillY = y + h - 1 - barH;
        int fillW = w - 2;

        // Simple solid-color bar; fluid texture can be re-added after sync is proven stable
        int color = fallbackColor;
        if (!stack.isEmpty() && stack.getFluid() != null) {
            var ext = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(stack.getFluid());
            color = ext.getTintColor(stack) | 0xFF000000;
        }
        g.fill(fillX, fillY, fillX + fillW, fillY + barH, color);
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