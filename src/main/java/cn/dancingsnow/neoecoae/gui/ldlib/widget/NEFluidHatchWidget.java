package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeFluidStorage;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class NEFluidHatchWidget extends NELDLibMachineWidget {
    public static final int INPUT_UI_WIDTH = 172;
    public static final int INPUT_UI_HEIGHT = 142;
    public static final int OUTPUT_UI_WIDTH = INPUT_UI_WIDTH;
    public static final int OUTPUT_UI_HEIGHT = INPUT_UI_HEIGHT;

    private static final int SLOT_SIZE = 18;
    private static final int INVENTORY_WIDTH = SLOT_SIZE * 9;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 8;
    private static final int TITLE_HEIGHT = 9;
    private static final int TANK_Y = 28;
    private static final int INVENTORY_Y = 60;
    private static final int HOTBAR_Y = 119;
    private static final ResourceTexture SLOT_TEXTURE = new ResourceTexture(
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/slot.png"));

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
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addText(
                TITLE_X,
                TITLE_Y,
                width - TITLE_X * 2,
                TITLE_HEIGHT,
                () -> title,
                TEXT_PRIMARY,
                TextTexture.TextType.LEFT_HIDE);
        addWidget(new TankWidget(new NEForgeFluidStorage(tank), tankX(), TANK_Y, SLOT_SIZE, SLOT_SIZE, input, true)
                .setBackground(SLOT_TEXTURE)
                .setShowAmount(true)
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                .setAllowClickFilled(input)
                .setAllowClickDrained(true)
                .setChangeListener(() -> {}));
        addPlayerInventorySlots();
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawPlayerInventoryBackground(graphics);
    }

    private void addPlayerInventorySlots() {
        NEPlayerInventoryWidgets.addPlayerInventorySlots(
                this, playerInventory, inventoryX(), INVENTORY_Y, HOTBAR_Y);
    }

    private void drawPlayerInventoryBackground(GuiGraphics graphics) {
        NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                graphics,
                this::absX,
                this::absY,
                inventoryX(),
                INVENTORY_Y,
                HOTBAR_Y);
    }

    private int inventoryX() {
        return (width - INVENTORY_WIDTH) / 2;
    }

    private int tankX() {
        return (width - SLOT_SIZE) / 2;
    }
}
