package cn.dancingsnow.neoecoae.gui.ldlib;

import static cn.dancingsnow.neoecoae.gui.nativeui.layout.NEIntegratedWorkingStationLayout.*;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NENativeAe2StyleRenderer;
import cn.dancingsnow.neoecoae.network.NENetwork;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.side.fluid.IFluidStorage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;

/**
 * LDLib1 client layer for the Integrated Working Station.
 *
 * <p>The existing menu remains authoritative for all real slots. This screen
 * keeps the native slot coordinates and only replaces client-side widgets such
 * as buttons, progress, and fluid display.
 */
public class NEIntegratedWorkingStationLDLibUI extends NELDLibMachineScreen<NEIntegratedWorkingStationMenu> {
    private static final int TANK_CAPACITY = 16000;
    private static final IGuiTexture BUTTON_TEXTURE =
            new GuiTextureGroup(new ColorRectAndBorderTexture(0xFFECEEF3, 0xFF707070, 1.0F));
    private static final IGuiTexture BUTTON_HOVER_TEXTURE =
            new GuiTextureGroup(new ColorRectAndBorderTexture(0x00000000, 0xFF4F7FB6, 1.0F));
    private static final IGuiTexture TANK_BACKGROUND = new GuiTextureGroup(
            new ColorRectTexture(0xFF8E8E8E), new ColorRectAndBorderTexture(0x00000000, 0xFF707070, 1.0F));

    private ButtonWidget autoExportButton;

    public NEIntegratedWorkingStationLDLibUI(
            NEIntegratedWorkingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, PANEL_W, PANEL_H);
    }

    @Override
    protected void initLdWidgets() {
        this.autoExportButton = addLdWidget(new ButtonWidget(
                        TOGGLE_BTN_X,
                        TOGGLE_BTN_Y,
                        TOGGLE_BTN_W,
                        TOGGLE_BTN_H,
                        BUTTON_TEXTURE,
                        click -> sendAction(NENetwork.IWSAction.TOGGLE_AUTO_EXPORT)))
                .setHoverTexture(BUTTON_HOVER_TEXTURE);

        addLdWidget(new TankWidget(
                        new MenuFluidStorage(menu, true), FLUID_IN_X, FLUID_IN_Y, FLUID_IN_W, FLUID_IN_H, false, false)
                .setBackground(TANK_BACKGROUND)
                .setShowAmount(false)
                .setDrawHoverTips(false)
                .setDrawHoverOverlay(true)
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP));
        addLdWidget(new TankWidget(
                        new MenuFluidStorage(menu, false),
                        FLUID_OUT_X,
                        FLUID_OUT_Y,
                        FLUID_OUT_W,
                        FLUID_OUT_H,
                        false,
                        false)
                .setBackground(TANK_BACKGROUND)
                .setShowAmount(false)
                .setDrawHoverTips(false)
                .setDrawHoverOverlay(true)
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP));

        addProgress(
                PROGRESS_X,
                PROGRESS_Y,
                PROGRESS_W,
                PROGRESS_H,
                () -> percent(menu.getProgress(), menu.getMaxProgress()),
                0xFF49A36E,
                ProgressTexture.FillDirection.DOWN_TO_UP);

        sendAction(NENetwork.IWSAction.REQUEST_STATE);
    }

    @Override
    public boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        if (mouseX >= guiLeft && mouseX < guiLeft + PANEL_W && mouseY >= guiTop && mouseY < guiTop + PANEL_H) {
            return false;
        }
        if (mouseX >= guiLeft + UPGRADE_PANEL_X
                && mouseX < guiLeft + UPGRADE_PANEL_X + UPGRADE_PANEL_W
                && mouseY >= guiTop + UPGRADE_PANEL_Y
                && mouseY < guiTop + UPGRADE_PANEL_Y + UPGRADE_PANEL_H) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    public List<Rect2i> getJeiExtraAreas() {
        List<Rect2i> areas = new ArrayList<>();
        areas.add(new Rect2i(
                this.leftPos + UPGRADE_PANEL_X, this.topPos + UPGRADE_PANEL_Y, UPGRADE_PANEL_W, UPGRADE_PANEL_H));
        return areas;
    }

    @Override
    protected void renderLdBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        NENativeAe2StyleRenderer.drawAeMainPanel(g, leftPos, topPos, PANEL_W, PANEL_H);
        NENativeAe2StyleRenderer.drawAeUpgradePanel(
                g, leftPos + UPGRADE_PANEL_X, topPos + UPGRADE_PANEL_Y, UPGRADE_COUNT);

        drawInputSlots(g);
        NENativeAe2StyleRenderer.drawAeInscriberOutputFrame(
                g, leftPos + OUTPUT_FRAME_X, topPos + OUTPUT_FRAME_Y, OUTPUT_FRAME_W, OUTPUT_FRAME_H);
        drawPlayerInventorySlots(g);
        drawHotbarSlots(g);
        drawUpgradePlaceholders(g);
        drawClearFluidButtons(g, mouseX, mouseY);
    }

    @Override
    protected void renderLdForeground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawAutoExportIcon(g);
        g.drawString(font, title, leftPos + TITLE_X, topPos + TITLE_Y, TEXT_PRIMARY, false);
        g.drawString(
                font,
                Component.translatable("gui.neoecoae.common.inventory"),
                leftPos + INV_LABEL_X,
                topPos + INV_LABEL_Y,
                TEXT_MUTED,
                false);
    }

    @Override
    protected void renderLdTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (renderAutoExportTooltip(g, mouseX, mouseY)) {
            return;
        }
        if (renderProgressTooltip(g, mouseX, mouseY)) {
            return;
        }
        if (renderFluidTooltip(g, mouseX, mouseY, true)) {
            return;
        }
        if (renderFluidTooltip(g, mouseX, mouseY, false)) {
            return;
        }
        renderUpgradeTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            if (isMouseIn(CLEAR_BTN_IN_X, CLEAR_BTN_IN_Y, CLEAR_BTN_W, CLEAR_BTN_H, mx, my)) {
                sendAction(NENetwork.IWSAction.CLEAR_INPUT_FLUID);
                return true;
            }
            if (isMouseIn(CLEAR_BTN_OUT_X, CLEAR_BTN_OUT_Y, CLEAR_BTN_W, CLEAR_BTN_H, mx, my)) {
                sendAction(NENetwork.IWSAction.CLEAR_OUTPUT_FLUID);
                return true;
            }
            if (isMouseIn(FLUID_IN_X, FLUID_IN_Y, FLUID_IN_W, FLUID_IN_H, mx, my)) {
                sendAction(NENetwork.IWSAction.INPUT_TANK_CONTAINER_CLICK);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendAction(NENetwork.IWSAction action) {
        NENetwork.CHANNEL.sendToServer(
                new NENetwork.NEIntegratedWorkingStationActionPacket(menu.getMachinePos(), action));
    }

    private void drawInputSlots(GuiGraphics g) {
        for (int row = 0; row < INPUT_ROWS; row++) {
            for (int col = 0; col < INPUT_COLS; col++) {
                NENativeAe2StyleRenderer.drawAeSlot(
                        g, leftPos + INPUT_BG_X + col * SLOT_SIZE, topPos + INPUT_BG_Y + row * SLOT_SIZE);
            }
        }
    }

    private void drawPlayerInventorySlots(GuiGraphics g) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                NENativeAe2StyleRenderer.drawAeSlot(
                        g, leftPos + PLAYER_INV_BG_X + col * SLOT_SIZE, topPos + PLAYER_INV_BG_Y + row * SLOT_SIZE);
            }
        }
    }

    private void drawHotbarSlots(GuiGraphics g) {
        for (int col = 0; col < 9; col++) {
            NENativeAe2StyleRenderer.drawAeSlot(g, leftPos + HOTBAR_BG_X + col * SLOT_SIZE, topPos + HOTBAR_BG_Y);
        }
    }

    private void drawUpgradePlaceholders(GuiGraphics g) {
        int startUpgradeSlot = NEIntegratedWorkingStationMenu.INPUT_SLOTS + NEIntegratedWorkingStationMenu.OUTPUT_SLOTS;
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            int slotIdx = startUpgradeSlot + i;
            if (slotIdx < menu.slots.size() && !menu.getSlot(slotIdx).hasItem()) {
                NENativeAe2StyleRenderer.drawAeIcon(
                        g,
                        Icon.BACKGROUND_UPGRADE,
                        leftPos + UPGRADE_SLOT_X,
                        topPos + UPGRADE_FIRST_SLOT_Y + i * SLOT_SIZE,
                        0.4F);
            }
        }
    }

    private void drawAutoExportIcon(GuiGraphics g) {
        if (autoExportButton == null) {
            return;
        }
        Icon icon = menu.isAutoExportEnabled() ? Icon.AUTO_EXPORT_ON : Icon.AUTO_EXPORT_OFF;
        int iconX = leftPos + TOGGLE_BTN_X + (TOGGLE_BTN_W - icon.width) / 2;
        int iconY = topPos + TOGGLE_BTN_Y + (TOGGLE_BTN_H - icon.height) / 2;
        NENativeAe2StyleRenderer.drawAeIcon(g, icon, iconX, iconY);
    }

    private void drawClearFluidButtons(GuiGraphics g, int mouseX, int mouseY) {
        boolean hoverIn = isMouseIn(CLEAR_BTN_IN_X, CLEAR_BTN_IN_Y, CLEAR_BTN_W, CLEAR_BTN_H, mouseX, mouseY);
        boolean hoverOut = isMouseIn(CLEAR_BTN_OUT_X, CLEAR_BTN_OUT_Y, CLEAR_BTN_W, CLEAR_BTN_H, mouseX, mouseY);

        drawClearBar(g, leftPos + CLEAR_BTN_IN_X, topPos + CLEAR_BTN_IN_Y);
        drawClearBar(g, leftPos + CLEAR_BTN_OUT_X, topPos + CLEAR_BTN_OUT_Y);

        if (hoverIn) {
            drawSmallX5(g, leftPos + CLEAR_BTN_IN_X + 2, topPos + CLEAR_BTN_IN_Y + 2, 0x40000000);
            drawSmallX5(g, leftPos + CLEAR_BTN_IN_X + 1, topPos + CLEAR_BTN_IN_Y + 1, 0xFFFFFFFF);
        }
        if (hoverOut) {
            drawSmallX5(g, leftPos + CLEAR_BTN_OUT_X + 2, topPos + CLEAR_BTN_OUT_Y + 2, 0x40000000);
            drawSmallX5(g, leftPos + CLEAR_BTN_OUT_X + 1, topPos + CLEAR_BTN_OUT_Y + 1, 0xFFFFFFFF);
        }
    }

    private void drawClearBar(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 8, y + 8, 0x80505050);
        g.fill(x, y, x + 8, y + 1, 0xA0303030);
        g.fill(x, y, x + 1, y + 8, 0xA0303030);
        g.fill(x, y + 7, x + 8, y + 8, 0xA0FFFFFF);
        g.fill(x + 7, y, x + 8, y + 8, 0xA0FFFFFF);
    }

    private void drawSmallX5(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 1, y + 1, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
        g.fill(x + 3, y + 3, x + 4, y + 4, color);
        g.fill(x + 4, y + 4, x + 5, y + 5, color);
        g.fill(x + 4, y, x + 5, y + 1, color);
        g.fill(x + 3, y + 1, x + 4, y + 2, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
        g.fill(x + 1, y + 3, x + 2, y + 4, color);
        g.fill(x, y + 4, x + 1, y + 5, color);
    }

    private boolean renderProgressTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (!isMouseIn(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY)) {
            return false;
        }
        int maxProgress = menu.getMaxProgress();
        int pct = maxProgress > 0 ? menu.getProgress() * 100 / maxProgress : 0;
        g.renderComponentTooltip(
                font,
                List.of(Component.translatable("gui.neoecoae.integrated_working_station.progress_percent", pct)),
                mouseX,
                mouseY);
        return true;
    }

    private boolean renderAutoExportTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (!isMouseIn(TOGGLE_BTN_X, TOGGLE_BTN_Y, TOGGLE_BTN_W, TOGGLE_BTN_H, mouseX, mouseY)) {
            return false;
        }
        g.renderComponentTooltip(font, List.of(Component.translatable(autoExportTooltipKey())), mouseX, mouseY);
        return true;
    }

    private boolean renderFluidTooltip(GuiGraphics g, int mouseX, int mouseY, boolean input) {
        int x = input ? FLUID_IN_X : FLUID_OUT_X;
        int y = input ? FLUID_IN_Y : FLUID_OUT_Y;
        int w = input ? FLUID_IN_W : FLUID_OUT_W;
        int h = input ? FLUID_IN_H : FLUID_OUT_H;
        if (!isMouseIn(x, y, w, h, mouseX, mouseY)) {
            return false;
        }

        FluidStack stack = input ? menu.getClientInputFluid() : menu.getClientOutputFluid();
        int amount = input ? menu.getFluidInAmount() : menu.getFluidOutAmount();
        Component name =
                stack.isEmpty() ? Component.translatable("gui.neoecoae.fluid_tank.empty") : stack.getDisplayName();
        Component volume =
                Component.translatable("gui.neoecoae.fluid_tank.amount", fmt(Math.max(0, amount)), fmt(TANK_CAPACITY));
        g.renderComponentTooltip(font, List.of(name, volume), mouseX, mouseY);
        return true;
    }

    private void renderUpgradeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (!isMouseIn(UPGRADE_PANEL_X, UPGRADE_PANEL_Y, UPGRADE_PANEL_W, UPGRADE_PANEL_H, mouseX, mouseY)) {
            return;
        }
        g.renderComponentTooltip(
                font,
                List.of(
                        Component.translatable("gui.neoecoae.integrated_working_station.available_upgrades"),
                        Component.translatable("gui.neoecoae.integrated_working_station.speed_card_upgrade", 4)),
                mouseX,
                mouseY);
    }

    private String autoExportTooltipKey() {
        return menu.isAutoExportEnabled()
                ? "gui.neoecoae.integrated_working_station.auto_io.on"
                : "gui.neoecoae.integrated_working_station.auto_io.off";
    }

    private static final class MenuFluidStorage implements IFluidStorage {
        private final NEIntegratedWorkingStationMenu menu;
        private final boolean input;

        private MenuFluidStorage(NEIntegratedWorkingStationMenu menu, boolean input) {
            this.menu = menu;
            this.input = input;
        }

        @Override
        public com.lowdragmc.lowdraglib.side.fluid.FluidStack getFluid() {
            FluidStack stack = input ? menu.getClientInputFluid() : menu.getClientOutputFluid();
            int amount = input ? menu.getFluidInAmount() : menu.getFluidOutAmount();
            if (amount <= 0 || stack.isEmpty()) {
                return com.lowdragmc.lowdraglib.side.fluid.FluidStack.empty();
            }
            if (stack.hasTag()) {
                CompoundTag tag = stack.getTag();
                return com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(
                        stack.getFluid(), amount, tag == null ? null : tag.copy());
            }
            return com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(stack.getFluid(), amount);
        }

        @Override
        public void setFluid(com.lowdragmc.lowdraglib.side.fluid.FluidStack fluidStack) {}

        @Override
        public long getCapacity() {
            return TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(com.lowdragmc.lowdraglib.side.fluid.FluidStack fluidStack) {
            return true;
        }

        @Override
        public long fill(
                int tank,
                com.lowdragmc.lowdraglib.side.fluid.FluidStack resource,
                boolean simulate,
                boolean notifyChanges) {
            return 0;
        }

        @Override
        public boolean supportsFill(int tank) {
            return false;
        }

        @Override
        public com.lowdragmc.lowdraglib.side.fluid.FluidStack drain(
                int tank,
                com.lowdragmc.lowdraglib.side.fluid.FluidStack resource,
                boolean simulate,
                boolean notifyChanges) {
            return com.lowdragmc.lowdraglib.side.fluid.FluidStack.empty();
        }

        @Override
        public boolean supportsDrain(int tank) {
            return false;
        }

        @Override
        public Object createSnapshot() {
            return null;
        }

        @Override
        public void restoreFromSnapshot(Object snapshot) {}
    }
}
