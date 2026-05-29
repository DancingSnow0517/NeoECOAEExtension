package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.NENineSliceRenderer;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NEClearFluidButton;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NETexturedButton;
import cn.dancingsnow.neoecoae.network.NENetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class NEIntegratedWorkingStationScreen extends AbstractContainerScreen<NEIntegratedWorkingStationMenu> {
    private static final ResourceLocation TEX_BG = NeoECOAE.id("textures/gui/background.png");
    private static final ResourceLocation TEX_SLOT = NeoECOAE.id("textures/gui/slot.png");
    private static final ResourceLocation TEX_INV_BORDER = NeoECOAE.id("textures/gui/inventory_border.png");
    private static final ResourceLocation TEX_BAR_CONTAINER = NeoECOAE.id("textures/gui/bar_container.png");
    private static final int PANEL_W = 168, PANEL_H = 171, GUI_WIDTH = 168, GUI_HEIGHT = 171;
    private static final int SLOT_SIZE = 18, ITEM_OFFSET = 1;
    private static final int PROGRESS_X = 128, PROGRESS_Y = 32, PROGRESS_W = 6, PROGRESS_H = 18;
    private static final int FLUID_IN_X = 3, FLUID_IN_Y = 14, FLUID_IN_W = 18, FLUID_IN_H = 54;
    private static final int FLUID_OUT_X = 147, FLUID_OUT_Y = 14, FLUID_OUT_W = 18, FLUID_OUT_H = 54;
    private static final int INPUT_COLS = 3, INPUT_ROWS = 3, INPUT_BG_X = 39, INPUT_BG_Y = 14;
    private static final int OUTPUT_BG_X = 108, OUTPUT_BG_Y = 32;
    private static final int UPGRADE_BAR_X = 170, UPGRADE_BAR_Y = 1, UPGRADE_BAR_W = 22, UPGRADE_BAR_H = 76;
    private static final int UPGRADE_BG_X = 171, UPGRADE_FIRST_BG_Y = 2, UPGRADE_COUNT = 4;
    private static final int INV_BORDER_X = 2, INV_BORDER_Y = 87, INV_BORDER_W = 165, INV_BORDER_H = 56;
    private static final int INV_BG_X = 3, INV_BG_Y = 88;
    private static final int HOTBAR_BORDER_X = 2, HOTBAR_BORDER_Y = 146, HOTBAR_BORDER_W = 165, HOTBAR_BORDER_H = 21;
    private static final int HOTBAR_BG_X = 3, HOTBAR_BG_Y = 148;
    private static final int TXT_PRIMARY = 0xFF403E53, TXT_HINT = 0xFF2F5F8F;
    private static final int FLUID_EMPTY = 0xFFADB0C4, FLUID_BORDER = 0xFF403E53;
    private static final int FLUID_IN_COLOR = 0xFF3A7FD6, FLUID_OUT_COLOR = 0xFF8E7CFF;
    private static final int WATER_COLOR = 0xFF3A7FD6, LAVA_COLOR = 0xFFFF6A00;
    private static final int SETTINGS_PANEL_X = -20, SETTINGS_PANEL_Y = 1, SETTINGS_PANEL_W = 20, SETTINGS_PANEL_H = 24;
    private static final int TOGGLE_BTN_X = -19, TOGGLE_BTN_Y = 2, TOGGLE_BTN_W = 18, TOGGLE_BTN_H = 20;
    private static final int CLEAR_BTN_W = 8, CLEAR_BTN_IN_X = 20, CLEAR_BTN_OUT_X = 137, CLEAR_BTN_Y = 59;
    private static final int CARD_BORDER = 0xFF6F7288, CARD_FILL = 0xFFB5B8C8, CARD_LINE = 0xFF8E91A5;
    private NETexturedButton autoExportBtn;

    public NEIntegratedWorkingStationScreen(NEIntegratedWorkingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        autoExportBtn = new NETexturedButton(leftPos + TOGGLE_BTN_X, topPos + TOGGLE_BTN_Y, TOGGLE_BTN_W, TOGGLE_BTN_H,
            Component.literal("→"), btn -> sendAction(NENetwork.IWSAction.TOGGLE_AUTO_EXPORT));
        addRenderableWidget(autoExportBtn);
        addRenderableWidget(new NEClearFluidButton(leftPos + CLEAR_BTN_IN_X, topPos + CLEAR_BTN_Y,
            btn -> sendAction(NENetwork.IWSAction.CLEAR_INPUT_FLUID)));
        addRenderableWidget(new NEClearFluidButton(leftPos + CLEAR_BTN_OUT_X, topPos + CLEAR_BTN_Y,
            btn -> sendAction(NENetwork.IWSAction.CLEAR_OUTPUT_FLUID)));
    }

    private void sendAction(NENetwork.IWSAction action) {
        NENetwork.CHANNEL.sendToServer(new NENetwork.NEIntegratedWorkingStationActionPacket(menu.getMachinePos(), action));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (autoExportBtn != null) {
            boolean on = menu.isAutoExportEnabled();
            autoExportBtn.setMessage(Component.literal(on ? "→" : "×"));
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
        int px = leftPos + PROGRESS_X, py = topPos + PROGRESS_Y;
        if (mouseX >= px && mouseX < px + PROGRESS_W && mouseY >= py && mouseY < py + PROGRESS_H) {
            int progress = menu.getProgress();
            int maxProgress = menu.getMaxProgress();
            int pct = maxProgress > 0 ? progress * 100 / maxProgress : 0;
            g.renderTooltip(font, List.of(
                Component.translatable("gui.neoecoae.integrated_working_station.work_progress", progress, maxProgress).getVisualOrderText(),
                Component.translatable("gui.neoecoae.integrated_working_station.progress_percent", pct).getVisualOrderText()), mouseX, mouseY);
        }
    }

    private void renderUpgradeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int ux = leftPos + UPGRADE_BAR_X, uy = topPos + UPGRADE_BAR_Y;
        if (mouseX >= ux && mouseX < ux + UPGRADE_BAR_W && mouseY >= uy && mouseY < uy + UPGRADE_BAR_H) {
            g.renderTooltip(font, List.of(
                Component.translatable("gui.neoecoae.integrated_working_station.available_upgrades").getVisualOrderText(),
                Component.translatable("gui.neoecoae.integrated_working_station.speed_card_upgrade", 4).getVisualOrderText()), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        NENineSliceRenderer.drawPanel(g, TEX_BG, leftPos + SETTINGS_PANEL_X, topPos + SETTINGS_PANEL_Y,
            SETTINGS_PANEL_W, SETTINGS_PANEL_H, 16, 16, 2, 2, 2, 4);
        NENineSliceRenderer.drawPanel(g, TEX_BG, leftPos, topPos, PANEL_W, PANEL_H, 16, 16, 2, 2, 2, 4);
        NENineSliceRenderer.drawPanel(g, TEX_BG, leftPos + UPGRADE_BAR_X, topPos + UPGRADE_BAR_Y,
            UPGRADE_BAR_W, UPGRADE_BAR_H, 16, 16, 2, 2, 2, 4);
        NENineSliceRenderer.drawPanel(g, TEX_INV_BORDER, leftPos + INV_BORDER_X, topPos + INV_BORDER_Y,
            INV_BORDER_W, INV_BORDER_H, 16, 16, 1, 1, 1, 1);
        NENineSliceRenderer.drawPanel(g, TEX_INV_BORDER, leftPos + HOTBAR_BORDER_X, topPos + HOTBAR_BORDER_Y,
            HOTBAR_BORDER_W, HOTBAR_BORDER_H, 16, 16, 1, 1, 1, 1);
        drawSlots(g);
        drawFluidStackBar(g, leftPos + FLUID_IN_X, topPos + FLUID_IN_Y, FLUID_IN_W, FLUID_IN_H, true, FLUID_IN_COLOR);
        drawFluidStackBar(g, leftPos + FLUID_OUT_X, topPos + FLUID_OUT_Y, FLUID_OUT_W, FLUID_OUT_H, false, FLUID_OUT_COLOR);
        drawProgressBar(g, leftPos + PROGRESS_X, topPos + PROGRESS_Y, menu.getProgress(), menu.getMaxProgress());
        drawUpgradePlaceholders(g);
        drawFluidHover(g, mouseX, mouseY);
    }

    private void drawSlots(GuiGraphics g) {
        for (int row = 0; row < INPUT_ROWS; row++) for (int col = 0; col < INPUT_COLS; col++)
            drawSlot(g, leftPos + INPUT_BG_X + col * SLOT_SIZE, topPos + INPUT_BG_Y + row * SLOT_SIZE);
        drawSlot(g, leftPos + OUTPUT_BG_X, topPos + OUTPUT_BG_Y);
        for (int i = 0; i < UPGRADE_COUNT; i++) drawSlot(g, leftPos + UPGRADE_BG_X, topPos + UPGRADE_FIRST_BG_Y + i * SLOT_SIZE);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            drawSlot(g, leftPos + INV_BG_X + col * SLOT_SIZE, topPos + INV_BG_Y + row * SLOT_SIZE);
        for (int col = 0; col < 9; col++) drawSlot(g, leftPos + HOTBAR_BG_X + col * SLOT_SIZE, topPos + HOTBAR_BG_Y);
    }

    private void drawUpgradePlaceholders(GuiGraphics g) {
        int startUpgradeSlot = NEIntegratedWorkingStationMenu.INPUT_SLOTS + NEIntegratedWorkingStationMenu.OUTPUT_SLOTS;
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            int slotIdx = startUpgradeSlot + i;
            if (slotIdx < menu.slots.size() && !menu.getSlot(slotIdx).hasItem()) {
                int gx = leftPos + UPGRADE_BG_X + ITEM_OFFSET;
                int gy = topPos + UPGRADE_FIRST_BG_Y + ITEM_OFFSET + i * SLOT_SIZE;
                drawBlankCard(g, gx, gy);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 5, TXT_PRIMARY, false);
        g.drawString(font, Component.translatable("gui.neoecoae.common.inventory"), 3, 75, TXT_HINT, false);
    }

    private void drawBlankCard(GuiGraphics g, int gx, int gy) {
        g.fill(gx + 2, gy + 2, gx + 14, gy + 14, CARD_FILL);
        g.fill(gx + 2, gy + 2, gx + 14, gy + 3, CARD_BORDER);
        g.fill(gx + 2, gy + 13, gx + 14, gy + 14, CARD_BORDER);
        g.fill(gx + 2, gy + 2, gx + 3, gy + 14, CARD_BORDER);
        g.fill(gx + 13, gy + 2, gx + 14, gy + 14, CARD_BORDER);
        g.fill(gx + 4, gy + 7, gx + 12, gy + 8, CARD_LINE);
    }

    private void drawFluidHover(GuiGraphics g, int mouseX, int mouseY) {
        drawFluidHoverFor(g, mouseX, mouseY, true, leftPos + FLUID_IN_X, topPos + FLUID_IN_Y, FLUID_IN_W, FLUID_IN_H);
        drawFluidHoverFor(g, mouseX, mouseY, false, leftPos + FLUID_OUT_X, topPos + FLUID_OUT_Y, FLUID_OUT_W, FLUID_OUT_H);
    }

    private void drawFluidHoverFor(GuiGraphics g, int mouseX, int mouseY, boolean input, int x, int y, int w, int h) {
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return;
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x40FFFFFF);
        FluidStack stack = input ? menu.getClientInputFluid() : menu.getClientOutputFluid();
        int amount = input ? menu.getFluidInAmount() : menu.getFluidOutAmount();
        if (!stack.isEmpty()) {
            g.renderTooltip(font, List.of(stack.getDisplayName().getVisualOrderText(),
                Component.literal(stack.getAmount() + " / 16000 mB").getVisualOrderText()), mouseX, mouseY);
        } else {
            g.renderTooltip(font, Component.literal(amount + " / 16000 mB"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = (int) mouseX, my = (int) mouseY;
            if (mx >= leftPos + TOGGLE_BTN_X && mx < leftPos + TOGGLE_BTN_X + TOGGLE_BTN_W && my >= topPos + TOGGLE_BTN_Y && my < topPos + TOGGLE_BTN_Y + TOGGLE_BTN_H) {
                sendAction(NENetwork.IWSAction.TOGGLE_AUTO_EXPORT);
                return true;
            }
            if (mx >= leftPos + CLEAR_BTN_IN_X && mx < leftPos + CLEAR_BTN_IN_X + CLEAR_BTN_W && my >= topPos + CLEAR_BTN_Y && my < topPos + CLEAR_BTN_Y + CLEAR_BTN_W) {
                sendAction(NENetwork.IWSAction.CLEAR_INPUT_FLUID);
                return true;
            }
            if (mx >= leftPos + CLEAR_BTN_OUT_X && mx < leftPos + CLEAR_BTN_OUT_X + CLEAR_BTN_W && my >= topPos + CLEAR_BTN_Y && my < topPos + CLEAR_BTN_Y + CLEAR_BTN_W) {
                sendAction(NENetwork.IWSAction.CLEAR_OUTPUT_FLUID);
                return true;
            }
            if (mx >= leftPos + FLUID_IN_X && mx < leftPos + FLUID_IN_X + FLUID_IN_W && my >= topPos + FLUID_IN_Y && my < topPos + FLUID_IN_Y + FLUID_IN_H) {
                sendAction(NENetwork.IWSAction.INPUT_TANK_CONTAINER_CLICK);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.blit(TEX_SLOT, x, y, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
    }

    private int resolveFluidColor(FluidStack stack, int fallbackColor) {
        if (stack.isEmpty() || stack.getFluid() == null) return fallbackColor;
        if (stack.getFluid() == Fluids.WATER || stack.getFluid() == Fluids.FLOWING_WATER) return WATER_COLOR;
        if (stack.getFluid() == Fluids.LAVA || stack.getFluid() == Fluids.FLOWING_LAVA) return LAVA_COLOR;
        int tint = IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack);
        int rgb = tint & 0x00FFFFFF;
        if (rgb == 0x00FFFFFF || rgb == 0) return fallbackColor;
        return 0xFF000000 | rgb;
    }

    private void drawFluidTexture(GuiGraphics g, int x, int y, int w, int h, FluidStack stack, int fallbackColor) {
        if (stack.isEmpty() || stack.getFluid() == null) {
            g.fill(x, y, x + w, y + h, fallbackColor);
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = ext.getStillTexture(stack);
        if (stillTexture == null) {
            g.fill(x, y, x + w, y + h, resolveFluidColor(stack, fallbackColor));
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        int color = resolveFluidColor(stack, 0xFFFFFFFF);
        RenderSystem.setShaderColor(((color >>> 16) & 0xFF) / 255.0F, ((color >>> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, ((color >>> 24) & 0xFF) / 255.0F);
        g.enableScissor(x, y, x + w, y + h);
        for (int ty = y; ty < y + h; ty += 16) for (int tx = x; tx < x + w; tx += 16) g.blit(tx, ty, 0, 16, 16, sprite);
        g.disableScissor();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawFluidStackBar(GuiGraphics g, int x, int y, int w, int h, boolean input, int fallbackColor) {
        g.fill(x, y, x + w, y + h, FLUID_EMPTY);
        g.fill(x, y, x + w, y + 1, FLUID_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, FLUID_BORDER);
        g.fill(x, y, x + 1, y + h, FLUID_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, FLUID_BORDER);
        FluidStack stack = input ? menu.getClientInputFluid() : menu.getClientOutputFluid();
        int amount = stack.getAmount();
        if (amount <= 0) amount = input ? menu.getFluidInAmount() : menu.getFluidOutAmount();
        if (amount <= 0) return;
        int barH = Mth.clamp(amount * (h - 2) / 16000, 1, h - 2);
        drawFluidTexture(g, x + 1, y + h - 1 - barH, w - 2, barH, stack, fallbackColor);
    }

    private void drawProgressBar(GuiGraphics g, int x, int y, int progress, int max) {
        RenderSystem.setShaderTexture(0, TEX_BAR_CONTAINER);
        g.blit(TEX_BAR_CONTAINER, x, y, 0, 0, PROGRESS_W, PROGRESS_H, PROGRESS_W, PROGRESS_H);
        if (max > 0 && progress > 0) {
            int innerX = x + ITEM_OFFSET, innerY = y + ITEM_OFFSET;
            int innerW = PROGRESS_W - ITEM_OFFSET * 2, innerH = PROGRESS_H - ITEM_OFFSET * 2;
            int h = Mth.clamp(progress * innerH / max, 1, innerH);
            g.fill(innerX, innerY + innerH - h, innerX + innerW, innerY + innerH, 0xFF5A49D6);
        }
    }
}
