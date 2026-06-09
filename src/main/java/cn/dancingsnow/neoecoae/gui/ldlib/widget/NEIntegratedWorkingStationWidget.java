package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static cn.dancingsnow.neoecoae.gui.ldlib.layout.NEIntegratedWorkingStationLayout.*;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeFluidStorage;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeItemTransfer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEIntegratedWorkingStationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEInternalInventoryItemTransfer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;

public class NEIntegratedWorkingStationWidget extends NELDLibSyncedStateWidget<NEIntegratedWorkingStationUiState> {
    public static final int UI_WIDTH = -TOGGLE_BTN_X + UPGRADE_PANEL_X + UPGRADE_PANEL_W;
    public static final int UI_HEIGHT = PANEL_H;
    private static final int MAIN_X = -TOGGLE_BTN_X;
    private static final int AUTO_EXPORT_BUTTON_X = 0;

    private final ECOIntegratedWorkingStationBlockEntity station;
    private final Inventory playerInventory;
    private NEAe2IconButtonWidget autoExportButton;

    public NEIntegratedWorkingStationWidget(ECOIntegratedWorkingStationBlockEntity station, Inventory playerInventory) {
        super(
                station.getBlockState().getBlock().getName(),
                UI_WIDTH,
                UI_HEIGHT,
                NEIntegratedWorkingStationUiState.empty(),
                station::createIntegratedWorkingStationUiState,
                NELDLibStateCodecs::writeIntegratedWorkingStation,
                NELDLibStateCodecs::readIntegratedWorkingStation,
                10);
        this.station = station;
        this.playerInventory = playerInventory;
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected boolean shouldDrawBasePanel() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        var inputTransfer =
                new NEInternalInventoryItemTransfer(station.getInput(), station::onGuiInventoryChanged, true, true);
        var outputTransfer =
                new NEInternalInventoryItemTransfer(station.getOutput(), station::onGuiInventoryChanged, false, true);
        var upgradeTransfer = new NEForgeItemTransfer(station.getUpgradeItemHandler(), station::onGuiInventoryChanged);

        for (int row = 0; row < INPUT_ROWS; row++) {
            for (int col = 0; col < INPUT_COLS; col++) {
                addWidget(aeSlot(
                        inputTransfer,
                        col + row * INPUT_COLS,
                        mainX(INPUT_SLOT_X + col * SLOT_SIZE),
                        INPUT_SLOT_Y + row * SLOT_SIZE,
                        true,
                        true));
            }
        }

        addWidget(aeSlot(outputTransfer, 0, mainX(OUTPUT_SLOT_X), OUTPUT_SLOT_Y, false, true));

        for (int i = 0; i < UPGRADE_COUNT; i++) {
            addWidget(aeSlot(
                    upgradeTransfer, i, mainX(UPGRADE_SLOT_X), UPGRADE_FIRST_SLOT_Y + i * SLOT_SIZE, true, true));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addWidget(aeSlot(
                                playerInventory,
                                col + row * 9 + 9,
                                mainX(PLAYER_INV_SLOT_X + col * SLOT_SIZE),
                                PLAYER_INV_SLOT_Y + row * SLOT_SIZE,
                                true,
                                true)
                        .setLocationInfo(true, false));
            }
        }
        for (int col = 0; col < 9; col++) {
            addWidget(aeSlot(playerInventory, col, mainX(HOTBAR_SLOT_X + col * SLOT_SIZE), HOTBAR_SLOT_Y, true, true)
                    .setLocationInfo(true, true));
        }

        autoExportButton = new NEAe2IconButtonWidget(
                AUTO_EXPORT_BUTTON_X,
                TOGGLE_BTN_Y,
                TOGGLE_BTN_W,
                TOGGLE_BTN_H,
                currentState().autoExport() ? Icon.AUTO_EXPORT_ON : Icon.AUTO_EXPORT_OFF,
                click -> {
                    if (!click.isRemote) {
                        station.toggleAutoExport();
                        station.setChanged();
                        station.markForUpdate();
                        syncStateNow();
                    }
                });
        addWidget(autoExportButton);

        addWidget(new TankWidget(
                        new NEForgeFluidStorage(station.getInputTank()),
                        mainX(FLUID_IN_X),
                        FLUID_IN_Y,
                        FLUID_IN_W,
                        FLUID_IN_H,
                        true,
                        true)
                .setBackground(IGuiTexture.EMPTY)
                .setShowAmount(false)
                .setDrawHoverTips(false)
                .setDrawHoverOverlay(false)
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                .setAllowClickFilled(true)
                .setAllowClickDrained(true)
                .setChangeListener(station::onGuiInventoryChanged));
        addWidget(new TankWidget(
                        new NEForgeFluidStorage(station.getOutputTank()),
                        mainX(FLUID_OUT_X),
                        FLUID_OUT_Y,
                        FLUID_OUT_W,
                        FLUID_OUT_H,
                        true,
                        true)
                .setBackground(IGuiTexture.EMPTY)
                .setShowAmount(false)
                .setDrawHoverTips(false)
                .setDrawHoverOverlay(false)
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                .setAllowClickFilled(false)
                .setAllowClickDrained(true)
                .setChangeListener(station::onGuiInventoryChanged));
    }

    private SlotWidget aeSlot(IItemTransfer transfer, int index, int x, int y, boolean canTake, boolean canPut) {
        return new SlotWidget(transfer, index, x, y, canTake, canPut).setBackgroundTexture(IGuiTexture.EMPTY);
    }

    private SlotWidget aeSlot(Container container, int index, int x, int y, boolean canTake, boolean canPut) {
        return new SlotWidget(container, index, x, y, canTake, canPut).setBackgroundTexture(IGuiTexture.EMPTY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isMouseIn(CLEAR_BTN_IN_X, CLEAR_BTN_IN_Y, CLEAR_BTN_W, CLEAR_BTN_H, (int) mouseX, (int) mouseY)) {
                writeClientAction(2, buf -> buf.writeBoolean(true));
                return true;
            }
            if (isMouseIn(CLEAR_BTN_OUT_X, CLEAR_BTN_OUT_Y, CLEAR_BTN_W, CLEAR_BTN_H, (int) mouseX, (int) mouseY)) {
                writeClientAction(2, buf -> buf.writeBoolean(false));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleClientAction(int id, net.minecraft.network.FriendlyByteBuf buffer) {
        if (id == 2) {
            if (buffer.readBoolean()) {
                station.clearFluid();
            } else {
                station.clearFluidOut();
            }
            station.setChanged();
            station.markForUpdate();
            syncStateNow();
            return;
        }
        super.handleClientAction(id, buffer);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NELDLibAe2StyleRenderer.drawAeMainPanel(graphics, absX(MAIN_X), absY(0), PANEL_W, PANEL_H);
        NELDLibAe2StyleRenderer.drawAeUpgradePanel(
                graphics, absX(mainX(UPGRADE_PANEL_X)), absY(UPGRADE_PANEL_Y), UPGRADE_COUNT);

        for (int row = 0; row < INPUT_ROWS; row++) {
            for (int col = 0; col < INPUT_COLS; col++) {
                NELDLibAe2StyleRenderer.drawAeSlot(
                        graphics, absX(mainX(INPUT_BG_X + col * SLOT_SIZE)), absY(INPUT_BG_Y + row * SLOT_SIZE));
            }
        }
        NELDLibAe2StyleRenderer.drawAeInscriberOutputFrame(
                graphics, absX(mainX(OUTPUT_FRAME_X)), absY(OUTPUT_FRAME_Y), OUTPUT_FRAME_W, OUTPUT_FRAME_H);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                NELDLibAe2StyleRenderer.drawAeSlot(
                        graphics,
                        absX(mainX(PLAYER_INV_BG_X + col * SLOT_SIZE)),
                        absY(PLAYER_INV_BG_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            NELDLibAe2StyleRenderer.drawAeSlot(graphics, absX(mainX(HOTBAR_BG_X + col * SLOT_SIZE)), absY(HOTBAR_BG_Y));
        }
        drawFluidTanks(graphics, mouseX, mouseY);
        NELDLibAe2StyleRenderer.drawAeProgressBar(
                graphics,
                absX(mainX(PROGRESS_X)),
                absY(PROGRESS_Y),
                PROGRESS_W,
                PROGRESS_H,
                currentState().progress(),
                currentState().maxProgress());
        drawUpgradePlaceholders(graphics);
        drawClearFluidButtons(graphics, mouseX, mouseY);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        autoExportButton.setIcon(currentState().autoExport() ? Icon.AUTO_EXPORT_ON : Icon.AUTO_EXPORT_OFF);
        drawLocalString(graphics, title, mainX(TITLE_X), TITLE_Y, TEXT_PRIMARY);
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.common.inventory"),
                mainX(INV_LABEL_X),
                INV_LABEL_Y,
                TEXT_MUTED);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (renderAutoExportTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (renderProgressTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (renderFluidTooltip(graphics, mouseX, mouseY, true)) {
            return;
        }
        if (renderFluidTooltip(graphics, mouseX, mouseY, false)) {
            return;
        }
        renderUpgradeTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public List<Rect2i> getGuiExtraAreas(Rect2i guiRect, List<Rect2i> list) {
        List<Rect2i> areas = new ArrayList<>(super.getGuiExtraAreas(guiRect, list));
        areas.add(new Rect2i(absX(mainX(UPGRADE_PANEL_X)), absY(UPGRADE_PANEL_Y), UPGRADE_PANEL_W, UPGRADE_PANEL_H));
        return areas;
    }

    private void drawUpgradePlaceholders(GuiGraphics graphics) {
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            if (station.getUpgradeItemHandler().getStackInSlot(i).isEmpty()) {
                NELDLibAe2StyleRenderer.drawAeIcon(
                        graphics,
                        Icon.BACKGROUND_UPGRADE,
                        absX(mainX(UPGRADE_SLOT_X)),
                        absY(UPGRADE_FIRST_SLOT_Y + i * SLOT_SIZE),
                        0.4F);
            }
        }
    }

    private void drawFluidTanks(GuiGraphics graphics, int mouseX, int mouseY) {
        FluidStack input = currentState().inputFluid();
        FluidStack output = currentState().outputFluid();
        NELDLibAe2StyleRenderer.drawAeFluidTankSimple(
                graphics,
                absX(mainX(FLUID_IN_X)),
                absY(FLUID_IN_Y),
                FLUID_IN_W,
                FLUID_IN_H,
                input,
                Math.max(0, input.getAmount()),
                Math.max(0, station.getInputTank().getCapacity()));
        NELDLibAe2StyleRenderer.drawAeFluidTankSimple(
                graphics,
                absX(mainX(FLUID_OUT_X)),
                absY(FLUID_OUT_Y),
                FLUID_OUT_W,
                FLUID_OUT_H,
                output,
                Math.max(0, output.getAmount()),
                Math.max(0, station.getOutputTank().getCapacity()));
        drawFluidHover(graphics, mouseX, mouseY, true);
        drawFluidHover(graphics, mouseX, mouseY, false);
    }

    private void drawFluidHover(GuiGraphics graphics, int mouseX, int mouseY, boolean input) {
        int x = input ? FLUID_IN_X : FLUID_OUT_X;
        int y = input ? FLUID_IN_Y : FLUID_OUT_Y;
        int w = input ? FLUID_IN_W : FLUID_OUT_W;
        int h = input ? FLUID_IN_H : FLUID_OUT_H;
        if (!isMouseIn(x, y, w, h, mouseX, mouseY)) {
            return;
        }
        graphics.fill(
                absX(mainX(x + 1)), absY(y + 1), absX(mainX(x + w - 1)), absY(y + h - 1), NELDLibStyle.HOVER_OVERLAY);
    }

    private void drawClearFluidButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hoverIn = isMouseIn(CLEAR_BTN_IN_X, CLEAR_BTN_IN_Y, CLEAR_BTN_W, CLEAR_BTN_H, mouseX, mouseY);
        boolean hoverOut = isMouseIn(CLEAR_BTN_OUT_X, CLEAR_BTN_OUT_Y, CLEAR_BTN_W, CLEAR_BTN_H, mouseX, mouseY);

        drawClearBar(graphics, absX(mainX(CLEAR_BTN_IN_X)), absY(CLEAR_BTN_IN_Y));
        drawClearBar(graphics, absX(mainX(CLEAR_BTN_OUT_X)), absY(CLEAR_BTN_OUT_Y));

        if (hoverIn) {
            drawSmallX5(graphics, absX(mainX(CLEAR_BTN_IN_X + 2)), absY(CLEAR_BTN_IN_Y + 2), 0x40000000);
            drawSmallX5(graphics, absX(mainX(CLEAR_BTN_IN_X + 1)), absY(CLEAR_BTN_IN_Y + 1), 0xFFFFFFFF);
        }
        if (hoverOut) {
            drawSmallX5(graphics, absX(mainX(CLEAR_BTN_OUT_X + 2)), absY(CLEAR_BTN_OUT_Y + 2), 0x40000000);
            drawSmallX5(graphics, absX(mainX(CLEAR_BTN_OUT_X + 1)), absY(CLEAR_BTN_OUT_Y + 1), 0xFFFFFFFF);
        }
    }

    private void drawClearBar(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 8, y + 8, 0x80505050);
        graphics.fill(x, y, x + 8, y + 1, 0xA0303030);
        graphics.fill(x, y, x + 1, y + 8, 0xA0303030);
        graphics.fill(x, y + 7, x + 8, y + 8, 0xA0FFFFFF);
        graphics.fill(x + 7, y, x + 8, y + 8, 0xA0FFFFFF);
    }

    private void drawSmallX5(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 1, y + 1, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
        graphics.fill(x + 2, y + 2, x + 3, y + 3, color);
        graphics.fill(x + 3, y + 3, x + 4, y + 4, color);
        graphics.fill(x + 4, y + 4, x + 5, y + 5, color);
        graphics.fill(x + 4, y, x + 5, y + 1, color);
        graphics.fill(x + 3, y + 1, x + 4, y + 2, color);
        graphics.fill(x + 2, y + 2, x + 3, y + 3, color);
        graphics.fill(x + 1, y + 3, x + 2, y + 4, color);
        graphics.fill(x, y + 4, x + 1, y + 5, color);
    }

    private boolean renderAutoExportTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!super.isMouseIn(AUTO_EXPORT_BUTTON_X, TOGGLE_BTN_Y, TOGGLE_BTN_W, TOGGLE_BTN_H, mouseX, mouseY)) {
            return false;
        }
        graphics.renderComponentTooltip(
                font(),
                List.of(Component.translatable(
                        currentState().autoExport()
                                ? "gui.neoecoae.integrated_working_station.auto_io.on"
                                : "gui.neoecoae.integrated_working_station.auto_io.off")),
                mouseX,
                mouseY);
        return true;
    }

    private boolean renderProgressTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMouseIn(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY)) {
            return false;
        }
        int maxProgress = currentState().maxProgress();
        int pct = maxProgress > 0 ? currentState().progress() * 100 / maxProgress : 0;
        graphics.renderComponentTooltip(
                font(),
                List.of(Component.translatable("gui.neoecoae.integrated_working_station.progress_percent", pct)),
                mouseX,
                mouseY);
        return true;
    }

    private boolean renderFluidTooltip(GuiGraphics graphics, int mouseX, int mouseY, boolean input) {
        int x = input ? FLUID_IN_X : FLUID_OUT_X;
        int y = input ? FLUID_IN_Y : FLUID_OUT_Y;
        int w = input ? FLUID_IN_W : FLUID_OUT_W;
        int h = input ? FLUID_IN_H : FLUID_OUT_H;
        if (!isMouseIn(x, y, w, h, mouseX, mouseY)) {
            return false;
        }

        FluidStack stack = input ? currentState().inputFluid() : currentState().outputFluid();
        Component name =
                stack.isEmpty() ? Component.translatable("gui.neoecoae.fluid_tank.empty") : stack.getDisplayName();
        Component volume = Component.translatable(
                "gui.neoecoae.fluid_tank.amount",
                fmt(Math.max(0, stack.getAmount())),
                fmt(Math.max(
                        0,
                        input
                                ? station.getInputTank().getCapacity()
                                : station.getOutputTank().getCapacity())));
        graphics.renderComponentTooltip(font(), List.of(name, volume), mouseX, mouseY);
        return true;
    }

    private void renderUpgradeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMouseIn(UPGRADE_PANEL_X, UPGRADE_PANEL_Y, UPGRADE_PANEL_W, UPGRADE_PANEL_H, mouseX, mouseY)) {
            return;
        }
        graphics.renderComponentTooltip(
                font(),
                List.of(
                        Component.translatable("gui.neoecoae.integrated_working_station.available_upgrades"),
                        Component.translatable("gui.neoecoae.integrated_working_station.speed_card_upgrade", 4)),
                mouseX,
                mouseY);
    }

    private static int mainX(int x) {
        return MAIN_X + x;
    }

    @Override
    protected boolean isMouseIn(int x, int y, int w, int h, int mouseX, int mouseY) {
        return super.isMouseIn(mainX(x), y, w, h, mouseX, mouseY);
    }
}
