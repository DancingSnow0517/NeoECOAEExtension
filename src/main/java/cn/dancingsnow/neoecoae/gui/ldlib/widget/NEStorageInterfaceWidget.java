package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageInterfaceUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class NEStorageInterfaceWidget extends NELDLibSyncedStateWidget<NEStorageInterfaceUiState> {
    public static final int UI_WIDTH = 224;
    public static final int UI_HEIGHT = 116;

    private static final int ACTION_SET_MODE = 2;
    private static final int PANEL_X = 8;
    private static final int PANEL_Y = 24;
    private static final int PANEL_W = UI_WIDTH - 16;
    private static final int PANEL_H = UI_HEIGHT - 32;
    private static final int MODE_BUTTON_Y = PANEL_Y + 10;
    private static final int MODE_BUTTON_W = 62;
    private static final int MODE_BUTTON_H = 20;
    private static final int STORAGE_BUTTON_X = PANEL_X + 8;
    private static final int INPUT_BUTTON_X = PANEL_X + (PANEL_W - MODE_BUTTON_W) / 2;
    private static final int OUTPUT_BUTTON_X = PANEL_X + PANEL_W - MODE_BUTTON_W - 8;
    private static final int TEXT_X = PANEL_X + 10;
    private static final int TEXT_Y = PANEL_Y + 40;
    private static final int TEXT_STEP = 12;
    private static final int STATUS_VALUE_X = TEXT_X + 72;

    private final ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface;

    public NEStorageInterfaceWidget(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface, Player player) {
        super(
                storageInterface.getBlockState().getBlock().getName(),
                UI_WIDTH,
                UI_HEIGHT,
                NEStorageInterfaceUiState.empty(storageInterface.getBlockPos()),
                storageInterface::createStorageInterfaceUiState,
                NELDLibStateCodecs::writeStorageInterface,
                NELDLibStateCodecs::readStorageInterface,
                10);
        this.storageInterface = storageInterface;
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addModeButton(
                STORAGE_BUTTON_X,
                Component.translatable("gui.neoecoae.storage_interface.mode.storage"),
                ECOStorageInterfaceMode.STORAGE);
        addModeButton(
                INPUT_BUTTON_X,
                Component.translatable("gui.neoecoae.storage_interface.mode.input"),
                ECOStorageInterfaceMode.INPUT);
        addModeButton(
                OUTPUT_BUTTON_X,
                Component.translatable("gui.neoecoae.storage_interface.mode.output"),
                ECOStorageInterfaceMode.OUTPUT);
    }

    private void addModeButton(int x, Component label, ECOStorageInterfaceMode mode) {
        addWidget(new NEAe2TextButtonWidget(
                x,
                MODE_BUTTON_Y,
                MODE_BUTTON_W,
                MODE_BUTTON_H,
                () -> label,
                click -> {
                    if (click.isRemote) {
                        writeClientAction(ACTION_SET_MODE, buf -> buf.writeEnum(mode));
                    } else {
                        storageInterface.setStorageInterfaceMode(mode);
                        syncStateNow();
                    }
                },
                () -> currentState().mode() == mode));
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == ACTION_SET_MODE) {
            storageInterface.setStorageInterfaceMode(buffer.readEnum(ECOStorageInterfaceMode.class));
            syncStateNow();
            return;
        }
        super.handleClientAction(id, buffer);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NELDLibClientStyle.drawDarkInsetRect(graphics, absX(PANEL_X), absY(PANEL_Y), PANEL_W, PANEL_H);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NEStorageInterfaceUiState state = currentState();
        drawLocalString(graphics, Component.translatable("gui.neoecoae.storage_interface.title"), 8, 8, TEXT_PRIMARY);

        int y = TEXT_Y;
        drawStatusLine(
                graphics,
                Component.translatable("gui.neoecoae.storage_interface.structure"),
                state.formed()
                        ? Component.translatable("gui.neoecoae.storage_interface.formed")
                        : Component.translatable("gui.neoecoae.storage_interface.unformed"),
                state.formed(),
                y);
        y += TEXT_STEP;
        drawStatusLine(
                graphics,
                Component.translatable("gui.neoecoae.storage_interface.network"),
                state.targetOnline()
                        ? Component.translatable("gui.neoecoae.storage_interface.connected")
                        : Component.translatable("gui.neoecoae.storage_interface.disconnected"),
                state.targetOnline(),
                y);
        y += TEXT_STEP;
        if (state.mode() == ECOStorageInterfaceMode.INPUT) {
            drawLocalString(
                    graphics,
                    Component.translatable(
                            "gui.neoecoae.storage_interface.import", NELDLibText.number(state.exportedLastTick())),
                    TEXT_X,
                    y,
                    NELDLibStyle.DARK_TEXT_VALUE);
        } else if (state.mode() == ECOStorageInterfaceMode.OUTPUT) {
            drawLocalString(
                    graphics,
                    Component.translatable(
                            "gui.neoecoae.storage_interface.export", NELDLibText.number(state.exportedLastTick())),
                    TEXT_X,
                    y,
                    NELDLibStyle.DARK_TEXT_VALUE);
        } else {
            drawLocalString(
                    graphics,
                    Component.translatable("gui.neoecoae.storage_interface.storage_mode"),
                    TEXT_X,
                    y,
                    NELDLibStyle.DARK_TEXT_MUTED);
        }
    }

    private void drawStatusLine(GuiGraphics graphics, Component label, Component value, boolean ok, int y) {
        drawLocalString(graphics, label.copy().append(": "), TEXT_X, y, NELDLibStyle.DARK_TEXT_MUTED);
        drawLocalString(
                graphics, value, STATUS_VALUE_X, y, ok ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(INPUT_BUTTON_X, MODE_BUTTON_Y, MODE_BUTTON_W, MODE_BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.storage_interface.input_tooltip")),
                    mouseX,
                    mouseY);
        }
        if (isMouseIn(OUTPUT_BUTTON_X, MODE_BUTTON_Y, MODE_BUTTON_W, MODE_BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.storage_interface.output_tooltip")),
                    mouseX,
                    mouseY);
        }
    }
}
