package cn.dancingsnow.neoecoae.gui.ldlib;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEFluidHatchMenu;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.side.fluid.IFluidStorage;
import com.lowdragmc.lowdraglib.utils.Position;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.FluidStack;

/**
 * LDLib1 screen for ECO Fluid Input/Output Hatches.
 *
 * <p>The existing {@link NEFluidHatchMenu} remains the authoritative state and
 * click target; this class only replaces the client-side drawing widgets.
 */
public class NEFluidHatchLDLibUI extends AbstractContainerScreen<NEFluidHatchMenu> {
    private static final int TANK_W = 46;
    private static final int TANK_H = 64;
    private static final int TANK_X = (NENativeUiConstants.UI_WIDTH - TANK_W) / 2;
    private static final int TANK_Y = 25;
    private static final int AMOUNT_Y = TANK_Y + TANK_H + 7;

    private static final IGuiTexture BACKGROUND =
            new GuiTextureGroup(new ColorRectTexture(0xFFE8E8E8), ResourceBorderTexture.BORDERED_BACKGROUND.copy());

    private final MenuFluidStorage fluidStorage;

    private TankWidget tankWidget;
    private TextTextureWidget titleWidget;
    private TextTextureWidget amountWidget;

    public NEFluidHatchLDLibUI(NEFluidHatchMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = NENativeUiConstants.UI_WIDTH;
        this.imageHeight = NENativeUiConstants.UI_HEIGHT;
        this.fluidStorage = new MenuFluidStorage(menu);
    }

    @Override
    protected void init() {
        super.init();
        Position parent = new Position(leftPos, topPos);

        this.titleWidget = new TextTextureWidget(
                        NENativeUiConstants.TITLE_X, NENativeUiConstants.TITLE_Y, imageWidth - 16, 9)
                .setText(title);
        this.titleWidget
                .textureStyle(texture -> texture.setColor(NENativeUiConstants.MACHINE_TEXT_PRIMARY)
                        .setDropShadow(false)
                        .setType(TextTexture.TextType.LEFT_HIDE))
                .setClientSideWidget();
        this.titleWidget.setParentPosition(parent);
        this.titleWidget.initWidget();

        this.tankWidget = new TankWidget(fluidStorage, TANK_X, TANK_Y, TANK_W, TANK_H, false, false)
                .setShowAmount(false)
                .setDrawHoverTips(false)
                .setDrawHoverOverlay(true)
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                .setClientSideWidget();
        this.tankWidget.setParentPosition(parent);
        this.tankWidget.initWidget();

        this.amountWidget = new TextTextureWidget(0, AMOUNT_Y, imageWidth, 9).setText(this::amountText);
        this.amountWidget
                .textureStyle(texture -> texture.setColor(NENativeUiConstants.MACHINE_TEXT_VALUE)
                        .setDropShadow(false)
                        .setType(TextTexture.TextType.NORMAL))
                .setClientSideWidget();
        this.amountWidget.setParentPosition(parent);
        this.amountWidget.initWidget();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (titleWidget != null) {
            titleWidget.updateScreen();
        }
        if (amountWidget != null) {
            amountWidget.updateScreen();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderFluidTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        BACKGROUND.draw(guiGraphics, mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight);
        if (titleWidget != null) {
            titleWidget.updateScreen();
            titleWidget.drawInBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (tankWidget != null) {
            tankWidget.drawInBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (amountWidget != null) {
            amountWidget.updateScreen();
            amountWidget.drawInBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInTank(mouseX, mouseY)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInTank(double mouseX, double mouseY) {
        return mouseX >= leftPos + TANK_X
                && mouseX < leftPos + TANK_X + TANK_W
                && mouseY >= topPos + TANK_Y
                && mouseY < topPos + TANK_Y + TANK_H;
    }

    private void renderFluidTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!isInTank(mouseX, mouseY)) {
            return;
        }

        FluidStack stack = menu.getClientFluid();
        Component name =
                stack.isEmpty() ? Component.translatable("gui.neoecoae.fluid_tank.empty") : stack.getDisplayName();
        guiGraphics.renderComponentTooltip(font, List.of(name, amountText()), mouseX, mouseY);
    }

    private Component amountText() {
        return Component.translatable(
                "gui.neoecoae.fluid_tank.amount",
                format(Math.max(0, menu.getTankAmount())),
                format(Math.max(0, menu.getTankCapacity())));
    }

    private static String format(int value) {
        return NumberFormat.getNumberInstance(Locale.US).format(value);
    }

    /**
     * Minimal read-only adapter from the existing Forge-backed menu state to
     * LDLib1's fluid interfaces. Server-side fluid interaction still goes
     * through {@link NEFluidHatchMenu#clickMenuButton(Player, int)}.
     */
    private static final class MenuFluidStorage implements IFluidStorage {
        private final NEFluidHatchMenu menu;

        private MenuFluidStorage(NEFluidHatchMenu menu) {
            this.menu = menu;
        }

        @Override
        public com.lowdragmc.lowdraglib.side.fluid.FluidStack getFluid() {
            FluidStack stack = menu.getClientFluid();
            int amount = Math.max(0, menu.getTankAmount());
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
            return Math.max(0, menu.getTankCapacity());
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
