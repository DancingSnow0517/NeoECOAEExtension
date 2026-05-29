package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NEAe2IconButton;
import cn.dancingsnow.neoecoae.network.NENetwork;
import appeng.client.gui.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

import static cn.dancingsnow.neoecoae.gui.nativeui.layout.NEIntegratedWorkingStationLayout.*;

public class NEIntegratedWorkingStationScreen extends AbstractContainerScreen<NEIntegratedWorkingStationMenu> {

    private static final int TXT_PRIMARY = 0xFF404040;
    private static final int TXT_HINT = 0xFF606060;

    private NEAe2IconButton autoExportBtn;

    public NEIntegratedWorkingStationScreen(NEIntegratedWorkingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void init() {
        super.init();
        // Auto-export toggle button (inside main panel, top-right)
        autoExportBtn = new NEAe2IconButton(
            leftPos + TOGGLE_BTN_X, topPos + TOGGLE_BTN_Y,
            TOGGLE_BTN_W, TOGGLE_BTN_H,
            Component.translatable("gui.neoecoae.integrated_working_station.auto_io"),
            btn -> sendAction(NENetwork.IWSAction.TOGGLE_AUTO_EXPORT));
        autoExportBtn.setIcons(Icon.AUTO_EXPORT_ON, Icon.AUTO_EXPORT_OFF);
        addRenderableWidget(autoExportBtn);

        // Send REQUEST_STATE on first open to get fluid state immediately
        sendAction(NENetwork.IWSAction.REQUEST_STATE);
    }

    /**
     * Treat clicks within the main panel and right upgrade panel as "inside".
     */
    @Override
    public boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        if (mouseX >= guiLeft && mouseX < guiLeft + PANEL_W
            && mouseY >= guiTop && mouseY < guiTop + PANEL_H) {
            return false;
        }
        // Right upgrade panel (full padded area)
        if (mouseX >= guiLeft + UPGRADE_PANEL_X
            && mouseX < guiLeft + UPGRADE_PANEL_X + UPGRADE_PANEL_W
            && mouseY >= guiTop + UPGRADE_PANEL_Y
            && mouseY < guiTop + UPGRADE_PANEL_Y + UPGRADE_PANEL_H) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    private void sendAction(NENetwork.IWSAction action) {
        NENetwork.CHANNEL.sendToServer(new NENetwork.NEIntegratedWorkingStationActionPacket(menu.getMachinePos(), action));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Update auto-export button toggle state and tooltip
        if (autoExportBtn != null) {
            boolean on = menu.isAutoExportEnabled();
            autoExportBtn.setToggled(on);
            autoExportBtn.setTooltip(Tooltip.create(Component.translatable(on
                ? "gui.neoecoae.integrated_working_station.auto_io.on"
                : "gui.neoecoae.integrated_working_station.auto_io.off")));
        }
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderProgressTooltip(g, mouseX, mouseY);
        renderUpgradeTooltip(g, mouseX, mouseY);
    }

    private void renderProgressTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int px = leftPos + PROGRESS_X;
        int py = topPos + PROGRESS_Y;
        if (mouseX >= px && mouseX < px + PROGRESS_W && mouseY >= py && mouseY < py + PROGRESS_H) {
            int progress = menu.getProgress();
            int maxProgress = menu.getMaxProgress();
            int pct = maxProgress > 0 ? progress * 100 / maxProgress : 0;
            g.renderTooltip(font, List.of(
                Component.translatable("gui.neoecoae.integrated_working_station.progress_percent", pct)
                    .getVisualOrderText()), mouseX, mouseY);
        }
    }

    private void renderUpgradeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int ux = leftPos + UPGRADE_PANEL_X;
        int uy = topPos + UPGRADE_PANEL_Y;
        if (mouseX >= ux && mouseX < ux + UPGRADE_PANEL_W
            && mouseY >= uy && mouseY < uy + UPGRADE_PANEL_H) {
            g.renderTooltip(font, List.of(
                Component.translatable("gui.neoecoae.integrated_working_station.available_upgrades")
                    .getVisualOrderText(),
                Component.translatable("gui.neoecoae.integrated_working_station.speed_card_upgrade", 4)
                    .getVisualOrderText()), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // 1. Main panel (AE2 BackgroundGenerator — provides the full visual background)
        NENativeAe2StyleRenderer.drawAeMainPanel(g, x, y, PANEL_W, PANEL_H);

        // 2. Upgrade panel (right side, AE2 extra_panels.png)
        NENativeAe2StyleRenderer.drawAeUpgradePanel(g,
            x + UPGRADE_PANEL_X, y + UPGRADE_PANEL_Y, UPGRADE_COUNT);

        // 3. Draw ordinary AE2 slots — no group panels, slots sit directly on main bg
        drawInputSlots(g, x, y);
        drawOutputSlot(g, x, y);
        drawPlayerInventorySlots(g, x, y);
        drawHotbarSlots(g, x, y);

        // 4. Fluid tanks
        drawFluidTank(g, x + FLUID_IN_X, y + FLUID_IN_Y, FLUID_IN_W, FLUID_IN_H, true);
        drawFluidTank(g, x + FLUID_OUT_X, y + FLUID_OUT_Y, FLUID_OUT_W, FLUID_OUT_H, false);

        // 5. Progress bar
        NENativeAe2StyleRenderer.drawAeProgressBar(g,
            x + PROGRESS_X, y + PROGRESS_Y, PROGRESS_W, PROGRESS_H,
            menu.getProgress(), menu.getMaxProgress());

        // 6. Upgrade placeholders (empty upgrade slots → BACKGROUND_UPGRADE)
        drawUpgradePlaceholders(g, x, y);

        // 7. Clear fluid buttons (hover-only X markers)
        drawClearFluidButtons(g, x, y, mouseX, mouseY);

        // 8. Fluid hover highlights
        drawFluidHover(g, mouseX, mouseY, x, y);
    }

    private void drawInputSlots(GuiGraphics g, int baseX, int baseY) {
        for (int row = 0; row < INPUT_ROWS; row++) {
            for (int col = 0; col < INPUT_COLS; col++) {
                NENativeAe2StyleRenderer.drawAeSlot(g,
                    baseX + INPUT_BG_X + col * SLOT_SIZE,
                    baseY + INPUT_BG_Y + row * SLOT_SIZE);
            }
        }
    }

    private void drawOutputSlot(GuiGraphics g, int baseX, int baseY) {
        NENativeAe2StyleRenderer.drawAeInscriberOutputFrame(g,
            baseX + OUTPUT_FRAME_X,
            baseY + OUTPUT_FRAME_Y,
            OUTPUT_FRAME_W,
            OUTPUT_FRAME_H);
    }

    private void drawPlayerInventorySlots(GuiGraphics g, int baseX, int baseY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                NENativeAe2StyleRenderer.drawAeSlot(g,
                    baseX + PLAYER_INV_BG_X + col * SLOT_SIZE,
                    baseY + PLAYER_INV_BG_Y + row * SLOT_SIZE);
            }
        }
    }

    private void drawHotbarSlots(GuiGraphics g, int baseX, int baseY) {
        for (int col = 0; col < 9; col++) {
            NENativeAe2StyleRenderer.drawAeSlot(g,
                baseX + HOTBAR_BG_X + col * SLOT_SIZE,
                baseY + HOTBAR_BG_Y);
        }
    }

    private void drawUpgradePlaceholders(GuiGraphics g, int baseX, int baseY) {
        int startUpgradeSlot = NEIntegratedWorkingStationMenu.INPUT_SLOTS + NEIntegratedWorkingStationMenu.OUTPUT_SLOTS;
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            int slotIdx = startUpgradeSlot + i;
            if (slotIdx < menu.slots.size() && !menu.getSlot(slotIdx).hasItem()) {
                // Draw BACKGROUND_UPGRADE at the item/click position (16×16 centred in 18×18)
                int gx = baseX + UPGRADE_SLOT_X;
                int gy = baseY + UPGRADE_FIRST_SLOT_Y + i * SLOT_SIZE;
                NENativeAe2StyleRenderer.drawAeIcon(g, Icon.BACKGROUND_UPGRADE, gx, gy, 0.4F);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, TITLE_X, TITLE_Y, TXT_PRIMARY, false);
        g.drawString(font, Component.translatable("gui.neoecoae.common.inventory"),
            INV_LABEL_X, INV_LABEL_Y, TXT_HINT, false);
    }

    // ── Fluid tank rendering ──

    private void drawFluidTank(GuiGraphics g, int x, int y, int w, int h, boolean input) {
        FluidStack stack = input ? menu.getClientInputFluid() : menu.getClientOutputFluid();
        int amount = stack.getAmount();
        if (amount <= 0) amount = input ? menu.getFluidInAmount() : menu.getFluidOutAmount();
        NENativeAe2StyleRenderer.drawAeFluidTank(g, x, y, w, h, stack, amount, 16000);
    }

    private void drawFluidHover(GuiGraphics g, int mouseX, int mouseY, int baseX, int baseY) {
        drawFluidHoverFor(g, mouseX, mouseY, true,
            baseX + FLUID_IN_X, baseY + FLUID_IN_Y, FLUID_IN_W, FLUID_IN_H);
        drawFluidHoverFor(g, mouseX, mouseY, false,
            baseX + FLUID_OUT_X, baseY + FLUID_OUT_Y, FLUID_OUT_W, FLUID_OUT_H);
    }

    private void drawFluidHoverFor(GuiGraphics g, int mouseX, int mouseY,
                                    boolean input, int x, int y, int w, int h) {
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return;
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x40FFFFFF);
        FluidStack stack = input ? menu.getClientInputFluid() : menu.getClientOutputFluid();
        int amount = input ? menu.getFluidInAmount() : menu.getFluidOutAmount();
        if (!stack.isEmpty()) {
            g.renderTooltip(font, List.of(
                stack.getDisplayName().getVisualOrderText(),
                Component.literal(stack.getAmount() + " / 16000 mB").getVisualOrderText()),
                mouseX, mouseY);
        } else {
            g.renderTooltip(font,
                Component.literal(amount + " / 16000 mB"), mouseX, mouseY);
        }
    }

    // ── Clear fluid buttons (常显 8×8 bar + hover 5×5 X) ──

    private void drawClearFluidButtons(GuiGraphics g, int baseX, int baseY,
                                        int mouseX, int mouseY) {
        boolean hoverIn = isMouseOverRect(mouseX, mouseY,
            baseX + CLEAR_BTN_IN_X,
            baseY + CLEAR_BTN_IN_Y,
            CLEAR_BTN_W,
            CLEAR_BTN_H);

        boolean hoverOut = isMouseOverRect(mouseX, mouseY,
            baseX + CLEAR_BTN_OUT_X,
            baseY + CLEAR_BTN_OUT_Y,
            CLEAR_BTN_W,
            CLEAR_BTN_H);

        // 常显 bar
        drawClearBar(g, baseX + CLEAR_BTN_IN_X, baseY + CLEAR_BTN_IN_Y);
        drawClearBar(g, baseX + CLEAR_BTN_OUT_X, baseY + CLEAR_BTN_OUT_Y);

        // hover 时显示 5×5 X
        if (hoverIn) {
            drawSmallX5(g, baseX + CLEAR_BTN_IN_X + 2, baseY + CLEAR_BTN_IN_Y + 2, 0x40000000);
            drawSmallX5(g, baseX + CLEAR_BTN_IN_X + 1, baseY + CLEAR_BTN_IN_Y + 1, 0xFFFFFFFF);
        }
        if (hoverOut) {
            drawSmallX5(g, baseX + CLEAR_BTN_OUT_X + 2, baseY + CLEAR_BTN_OUT_Y + 2, 0x40000000);
            drawSmallX5(g, baseX + CLEAR_BTN_OUT_X + 1, baseY + CLEAR_BTN_OUT_Y + 1, 0xFFFFFFFF);
        }
    }

    private boolean isMouseOverRect(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Draw the persistent 8×8 subtle backing bar (no X). */
    private void drawClearBar(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 8, y + 8, 0x80505050);
        g.fill(x, y, x + 8, y + 1, 0xA0303030);
        g.fill(x, y, x + 1, y + 8, 0xA0303030);
        g.fill(x, y + 7, x + 8, y + 8, 0xA0FFFFFF);
        g.fill(x + 7, y, x + 8, y + 8, 0xA0FFFFFF);
    }

    /** Draw a 5×5 X at the given position. */
    private void drawSmallX5(GuiGraphics g, int x, int y, int color) {
        // top-left to bottom-right
        g.fill(x,     y,     x + 1, y + 1, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
        g.fill(x + 3, y + 3, x + 4, y + 4, color);
        g.fill(x + 4, y + 4, x + 5, y + 5, color);

        // top-right to bottom-left
        g.fill(x + 4, y,     x + 5, y + 1, color);
        g.fill(x + 3, y + 1, x + 4, y + 2, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
        g.fill(x + 1, y + 3, x + 2, y + 4, color);
        g.fill(x,     y + 4, x + 1, y + 5, color);
    }

    // ── Mouse click handling ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            // Clear input fluid
            if (mx >= leftPos + CLEAR_BTN_IN_X
                && mx < leftPos + CLEAR_BTN_IN_X + CLEAR_BTN_SIZE
                && my >= topPos + CLEAR_BTN_IN_Y
                && my < topPos + CLEAR_BTN_IN_Y + CLEAR_BTN_SIZE) {
                sendAction(NENetwork.IWSAction.CLEAR_INPUT_FLUID);
                return true;
            }
            // Clear output fluid
            if (mx >= leftPos + CLEAR_BTN_OUT_X
                && mx < leftPos + CLEAR_BTN_OUT_X + CLEAR_BTN_SIZE
                && my >= topPos + CLEAR_BTN_OUT_Y
                && my < topPos + CLEAR_BTN_OUT_Y + CLEAR_BTN_SIZE) {
                sendAction(NENetwork.IWSAction.CLEAR_OUTPUT_FLUID);
                return true;
            }
            // Input fluid tank container click
            if (mx >= leftPos + FLUID_IN_X
                && mx < leftPos + FLUID_IN_X + FLUID_IN_W
                && my >= topPos + FLUID_IN_Y
                && my < topPos + FLUID_IN_Y + FLUID_IN_H) {
                sendAction(NENetwork.IWSAction.INPUT_TANK_CONTAINER_CLICK);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

