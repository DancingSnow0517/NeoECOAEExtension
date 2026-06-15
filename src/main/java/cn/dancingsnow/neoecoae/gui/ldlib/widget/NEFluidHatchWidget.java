package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeFluidStorage;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class NEFluidHatchWidget extends NELDLibMachineWidget {
    public static final int INPUT_UI_WIDTH = 176;
    public static final int INPUT_UI_HEIGHT = 188;
    public static final int OUTPUT_UI_WIDTH = 184;
    public static final int OUTPUT_UI_HEIGHT = 180;

    private static final int SLOT_SIZE = 18;
    private static final int INVENTORY_WIDTH = SLOT_SIZE * 9;
    private static final int TANK_W = 48;
    private static final int INPUT_TANK_H = 64;
    private static final int OUTPUT_TANK_H = 56;
    private static final int TANK_Y = 25;
    private static final int INVENTORY_GAP = 4;
    private static final int HOTBAR_GAP = 4;

    private final FluidTank tank;
    private final Inventory playerInventory;
    private final boolean input;

    public NEFluidHatchWidget(Component title, FluidTank tank, Inventory playerInventory, boolean input) {
        super(title, input ? INPUT_UI_WIDTH : OUTPUT_UI_WIDTH, input ? INPUT_UI_HEIGHT : OUTPUT_UI_HEIGHT);
        this.tank = tank;
        this.playerInventory = playerInventory;
        this.input = input;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new TankWidget(new NEForgeFluidStorage(tank), tankX(), TANK_Y, TANK_W, tankHeight(), true, true)
                .setBackground(IGuiTexture.EMPTY)
                .setShowAmount(false)
                .setDrawHoverTips(false)
                .setDrawHoverOverlay(false)
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                .setAllowClickFilled(true)
                .setAllowClickDrained(true)
                .setChangeListener(() -> {}));
        addText(8, amountY(), width - 16, 9, this::amountText, TEXT_VALUE, TextTexture.TextType.NORMAL);
        addPlayerInventorySlots();
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMouseIn(tankX(), TANK_Y, TANK_W, tankHeight(), mouseX, mouseY)) {
            return;
        }
        FluidStack stack = tank.getFluid();
        Component name =
                stack.isEmpty() ? Component.translatable("gui.neoecoae.fluid_tank.empty") : stack.getDisplayName();
        graphics.renderComponentTooltip(font(), List.of(name, amountText()), mouseX, mouseY);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        FluidStack stack = tank.getFluid();
        NELDLibAe2StyleRenderer.drawAeFluidTankSimple(
                graphics,
                absX(tankX()),
                absY(TANK_Y),
                TANK_W,
                tankHeight(),
                stack,
                Math.max(0, tank.getFluidAmount()),
                Math.max(0, tank.getCapacity()));
        if (isMouseIn(tankX(), TANK_Y, TANK_W, tankHeight(), mouseX, mouseY)) {
            graphics.fill(
                    absX(tankX() + 1),
                    absY(TANK_Y + 1),
                    absX(tankX() + TANK_W - 1),
                    absY(TANK_Y + tankHeight() - 1),
                    NELDLibStyle.HOVER_OVERLAY);
        }
        drawPlayerInventoryBackground(graphics);
    }

    private Component amountText() {
        return Component.translatable(
                "gui.neoecoae.fluid_tank.amount",
                fmt(Math.max(0, tank.getFluidAmount())),
                fmt(Math.max(0, tank.getCapacity())));
    }

    private void addPlayerInventorySlots() {
        NEPlayerInventoryWidgets.addPlayerInventorySlots(
                this, playerInventory, inventoryX(), inventoryY(), inventoryY() + SLOT_SIZE * 3 + HOTBAR_GAP);
    }

    private void drawPlayerInventoryBackground(GuiGraphics graphics) {
        NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                graphics,
                this::absX,
                this::absY,
                inventoryX(),
                inventoryY(),
                inventoryY() + SLOT_SIZE * 3 + HOTBAR_GAP);
    }

    private int tankX() {
        return (width - TANK_W) / 2;
    }

    private int tankHeight() {
        return input ? INPUT_TANK_H : OUTPUT_TANK_H;
    }

    private int amountY() {
        return TANK_Y + tankHeight() + 6;
    }

    private int inventoryX() {
        return (width - INVENTORY_WIDTH) / 2;
    }

    private int inventoryY() {
        return amountY() + 9 + INVENTORY_GAP;
    }
}
