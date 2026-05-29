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

    private static final int TXT_PRIMARY = 0xFFC6C6C6;
    private static final int TXT_HINT = 0xFF6A7F9A;

    private NEAe2IconButton autoExportBtn;

    public NEIntegratedWorkingStationScreen(NEIntegratedWorkingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = PANEL_W + UPGRADE_PANEL_X + 2; // Extend width to include upgrade panel
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void init() {
        super.init();
        // Auto-export toggle button using AE2 icon
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
        int uh = UPGRADE_COUNT * SLOT_SIZE;
        if (mouseX >= ux && mouseX < ux + SLOT_SIZE && mouseY >= uy && mouseY < uy + uh) {
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

        // 1. Main panel
        NENativeAe2StyleRenderer.drawAeMainPanel(g, x, y, PANEL_W, PANEL_H);

        // 2. Toolbar panel (left-side settings panel)
        NENativeAe2StyleRenderer.drawAeToolbarPanel(g,
            x + SETTINGS_PANEL_X, y + SETTINGS_PANEL_Y,
            SETTINGS_PANEL_W, SETTINGS_PANEL_H);

        // 3. Upgrade panel (right side, AE2 extra_panels.png)
        NENativeAe2StyleRenderer.drawAeUpgradePanel(g,
            x + UPGRADE_PANEL_X, y + UPGRADE_PANEL_Y, UPGRADE_COUNT);

        // 4. Draw ordinary AE2 slots (input 3×3, output, player inv, hotbar)
        // --- NOT upgrade slots, they are drawn by the upgrade panel ---
        drawInputSlots(g, x, y);
        drawOutputSlot(g, x, y);
        drawPlayerInventorySlots(g, x, y);
        drawHotbarSlots(g, x, y);

        // 5. Fluid tanks
        drawFluidTank(g, x + FLUID_IN_X, y + FLUID_IN_Y, FLUID_IN_W, FLUID_IN_H, true);
        drawFluidTank(g, x + FLUID_OUT_X, y + FLUID_OUT_Y, FLUID_OUT_W, FLUID_OUT_H, false);

        // 6. Progress bar
        NENativeAe2StyleRenderer.drawAeProgressBar(g,
            x + PROGRESS_X, y + PROGRESS_Y, PROGRESS_W, PROGRESS_H,
            menu.getProgress(), menu.getMaxProgress());

        // 7. Upgrade placeholders (empty upgrade slots → BACKGROUND_UPGRADE icon)
        drawUpgradePlaceholders(g, x, y);

        // 8. Fluid hover highlights
        drawFluidHover(g, mouseX, mouseY, x, y);

        // 9. Clear fluid buttons (AE2-style small buttons)
        drawClearFluidButtons(g, x, y, mouseX, mouseY);
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
        NENativeAe2StyleRenderer.drawAeSlot(g,
            baseX + OUTPUT_BG_X, baseY + OUTPUT_BG_Y);
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
                int gx = baseX + UPGRADE_SLOT_X;
                int gy = baseY + UPGRADE_FIRST_SLOT_Y + i * SLOT_SIZE;
                NENativeAe2StyleRenderer.drawAeIcon(g, Icon.BACKGROUND_UPGRADE, gx, gy, 0.4F);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 5, TXT_PRIMARY, false);
        g.drawString(font, Component.translatable("gui.neoecoae.common.inventory"),
            3, 75, TXT_HINT, false);
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

    // ── Clear fluid buttons (AE2-style small X buttons) ──

    private void drawClearFluidButtons(GuiGraphics g, int baseX, int baseY,
                                        int mouseX, int mouseY) {
        boolean hoverIn = mouseX >= baseX + CLEAR_BTN_IN_X
            && mouseX < baseX + CLEAR_BTN_IN_X + CLEAR_BTN_W
            && mouseY >= baseY + CLEAR_BTN_IN_Y
            && mouseY < baseY + CLEAR_BTN_IN_Y + CLEAR_BTN_H;
        boolean hoverOut = mouseX >= baseX + CLEAR_BTN_OUT_X
            && mouseX < baseX + CLEAR_BTN_OUT_X + CLEAR_BTN_W
            && mouseY >= baseY + CLEAR_BTN_OUT_Y
            && mouseY < baseY + CLEAR_BTN_OUT_Y + CLEAR_BTN_H;

        drawClearBtn(g, baseX + CLEAR_BTN_IN_X, baseY + CLEAR_BTN_IN_Y, hoverIn);
        drawClearBtn(g, baseX + CLEAR_BTN_OUT_X, baseY + CLEAR_BTN_OUT_Y, hoverOut);
    }

    private void drawClearBtn(GuiGraphics g, int x, int y, boolean hovered) {
        NENativeAe2StyleRenderer.drawAeToolbarButtonBackground(g, x, y,
            CLEAR_BTN_W, CLEAR_BTN_H, hovered, true);
        // Draw thin X on the button
        int cx = x;
        int cy = y;
        int color = hovered ? 0xFFFFFFFF : 0xFF8B8B8B;
        // top-left to bottom-right
        g.fill(cx + 1, cy + 1, cx + 2, cy + 2, color);
        g.fill(cx + 2, cy + 2, cx + 3, cy + 3, color);
        g.fill(cx + 3, cy + 3, cx + 4, cy + 4, color);
        g.fill(cx + 4, cy + 4, cx + 5, cy + 5, color);
        g.fill(cx + 5, cy + 5, cx + 6, cy + 6, color);
        g.fill(cx + 6, cy + 6, cx + 7, cy + 7, color);
        // top-right to bottom-left
        g.fill(cx + 6, cy + 1, cx + 7, cy + 2, color);
        g.fill(cx + 5, cy + 2, cx + 6, cy + 3, color);
        g.fill(cx + 4, cy + 3, cx + 5, cy + 4, color);
        g.fill(cx + 3, cy + 4, cx + 4, cy + 5, color);
        g.fill(cx + 2, cy + 5, cx + 3, cy + 6, color);
        g.fill(cx + 1, cy + 6, cx + 2, cy + 7, color);
    }

    // ── Mouse click handling ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            // Clear input fluid
            if (mx >= leftPos + CLEAR_BTN_IN_X
                && mx < leftPos + CLEAR_BTN_IN_X + CLEAR_BTN_W
                && my >= topPos + CLEAR_BTN_IN_Y
                && my < topPos + CLEAR_BTN_IN_Y + CLEAR_BTN_H) {
                sendAction(NENetwork.IWSAction.CLEAR_INPUT_FLUID);
                return true;
            }
            // Clear output fluid
            if (mx >= leftPos + CLEAR_BTN_OUT_X
                && mx < leftPos + CLEAR_BTN_OUT_X + CLEAR_BTN_W
                && my >= topPos + CLEAR_BTN_OUT_Y
                && my < topPos + CLEAR_BTN_OUT_Y + CLEAR_BTN_H) {
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

