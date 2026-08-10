package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingInterfaceUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeItemTransfer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** Unified, scrollable access terminal for the pattern buses attached to an ECO crafting network. */
public final class NECraftingInterfaceWidget extends NELDLibSyncedStateWidget<NECraftingInterfaceUiState> {
    public static final int UI_WIDTH = 194;
    public static final int UI_HEIGHT = 286;
    private static final int ACTION_TRANSFER = FIRST_CUSTOM_UPDATE_ID + 1;
    private static final int ACTION_SUBSTITUTIONS = FIRST_CUSTOM_UPDATE_ID + 2;
    private static final int ACTION_FLUID_SUBSTITUTIONS = FIRST_CUSTOM_UPDATE_ID + 3;
    private static final int ACTION_ORGANIZE = FIRST_CUSTOM_UPDATE_ID + 4;
    private static final int ACTION_SCROLL = FIRST_CUSTOM_UPDATE_ID + 5;
    private static final int GRID_X = 16;
    private static final int GRID_Y = 83;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLUMNS = 9;
    private static final int GRID_ROWS = 5;
    private static final int INV_X = 16;
    private static final int INV_Y = 198;
    private static final int HOTBAR_Y = 256;

    private final ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface;
    private final Inventory playerInventory;

    public NECraftingInterfaceWidget(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface, Player player) {
        super(
                craftingInterface.getBlockState().getBlock().getName(),
                UI_WIDTH,
                UI_HEIGHT,
                NECraftingInterfaceUiState.empty(craftingInterface.getBlockPos()),
                craftingInterface::createCraftingInterfaceUiState,
                NELDLibStateCodecs::writeCraftingInterface,
                NELDLibStateCodecs::readCraftingInterface,
                5);
        this.craftingInterface = craftingInterface;
        this.playerInventory = player.getInventory();
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new NEAe2TextButtonWidget(
                12,
                31,
                170,
                18,
                () -> Component.translatable("gui.neoecoae.host.crafting.pattern_transfer"),
                click -> {
                    if (click.isRemote) writeClientAction(ACTION_TRANSFER, buf -> {});
                    else {
                        craftingInterface.startNetworkPatternTransfer();
                        syncStateNow();
                    }
                },
                () -> false));
        addWidget(new TextFieldWidget(
                        12, 63, 116, 16, () -> currentState().search(), craftingInterface::setPatternPreviewSearch)
                .setMaxStringLength(128)
                .setBordered(true));
        addToolButton(130, "S", ACTION_SUBSTITUTIONS);
        addToolButton(148, "F", ACTION_FLUID_SUBSTITUTIONS);
        addToolButton(166, "=", ACTION_ORGANIZE);
        NEForgeItemTransfer transfer = new NEForgeItemTransfer(
                craftingInterface.getPatternPreviewItemHandler(), craftingInterface::setChanged);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int column = 0; column < GRID_COLUMNS; column++) {
                int slot = row * GRID_COLUMNS + column;
                addWidget(new SlotWidget(
                                transfer, slot, GRID_X + column * SLOT_SIZE, GRID_Y + row * SLOT_SIZE, true, true)
                        .setBackgroundTexture(IGuiTexture.EMPTY));
            }
        }
        NEPlayerInventoryWidgets.addPlayerInventorySlots(this, playerInventory, INV_X, INV_Y, HOTBAR_Y);
    }

    private void addToolButton(int x, String label, int action) {
        addWidget(new NEAe2TextButtonWidget(
                x,
                63,
                16,
                16,
                () -> Component.literal(label),
                click -> {
                    if (click.isRemote) writeClientAction(action, buf -> {});
                    else applyAction(action, 0);
                },
                () -> false,
                NEAe2TextButtonWidget.BackgroundStyle.TOOLBAR));
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id >= ACTION_TRANSFER && id <= ACTION_ORGANIZE) {
            applyAction(id, 0);
            return;
        }
        if (id == ACTION_SCROLL) {
            applyAction(id, buffer.readByte());
            return;
        }
        super.handleClientAction(id, buffer);
    }

    private void applyAction(int id, int value) {
        switch (id) {
            case ACTION_TRANSFER -> craftingInterface.startNetworkPatternTransfer();
            case ACTION_SUBSTITUTIONS -> craftingInterface.toggleSubstitutionPatterns();
            case ACTION_FLUID_SUBSTITUTIONS -> craftingInterface.toggleFluidSubstitutionPatterns();
            case ACTION_ORGANIZE -> craftingInterface.organizePatternBuses();
            case ACTION_SCROLL -> craftingInterface.scrollPatternPreview(value);
            default -> {
                return;
            }
        }
        syncStateNow();
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (isMouseIn(GRID_X, GRID_Y, GRID_COLUMNS * SLOT_SIZE, GRID_ROWS * SLOT_SIZE, (int) mouseX, (int) mouseY)) {
            int delta = wheelDelta < 0 ? 1 : -1;
            if (isRemote()) writeClientAction(ACTION_SCROLL, buf -> buf.writeByte(delta));
            else applyAction(ACTION_SCROLL, delta);
            return true;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NELDLibAe2StyleRenderer.drawAeMainPanel(graphics, getPositionX(), getPositionY(), UI_WIDTH, UI_HEIGHT);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int column = 0; column < GRID_COLUMNS; column++) {
                NELDLibAe2StyleRenderer.drawAeSlot(
                        graphics, absX(GRID_X + column * SLOT_SIZE), absY(GRID_Y + row * SLOT_SIZE));
            }
        }
        NEPlayerInventoryWidgets.drawPlayerInventorySlots(graphics, this::absX, this::absY, INV_X, INV_Y, HOTBAR_Y);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NECraftingInterfaceUiState state = currentState();
        drawLocalString(graphics, Component.translatable("gui.neoecoae.crafting_interface.title"), 8, 8, TEXT_PRIMARY);
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.storage_interface.network")
                        .append(": ")
                        .append(Component.translatable(
                                state.targetOnline()
                                        ? "gui.neoecoae.storage_interface.connected"
                                        : "gui.neoecoae.storage_interface.disconnected")),
                12,
                21,
                state.targetOnline() ? TEXT_SUCCESS : TEXT_ERROR);
        Component transfer = Component.translatable(
                state.transferStatusKey(), state.transferStatusArg1(), state.transferStatusArg2());
        drawLocalString(graphics, transfer, 12, 52, TEXT_MUTED);
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.crafting_interface.preview.slots", state.entryCount()),
                12,
                176,
                TEXT_MUTED);
        drawRightLocalString(
                graphics,
                Component.translatable(
                        "gui.neoecoae.crafting_interface.preview.scroll",
                        state.scrollRow() + 1,
                        state.maxScrollRow() + 1),
                182,
                176,
                TEXT_MUTED);
        drawLocalString(graphics, playerInventory.getDisplayName(), INV_X, 189, TEXT_MUTED);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(130, 63, 16, 16, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.crafting_interface.preview.filter_substitutions")),
                    mouseX,
                    mouseY);
        } else if (isMouseIn(148, 63, 16, 16, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable(
                            "gui.neoecoae.crafting_interface.preview.filter_fluid_substitutions")),
                    mouseX,
                    mouseY);
        } else if (isMouseIn(166, 63, 16, 16, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.crafting_interface.preview.organize")),
                    mouseX,
                    mouseY);
        }
    }
}
