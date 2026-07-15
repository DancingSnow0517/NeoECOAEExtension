package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageLayout.*;

import appeng.core.localization.GuiText;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuLocators;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.storage.NEStorageHeaderPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.storage.NEStorageMetricsPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.storage.NEStorageUsagePanel;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageLayout;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageScrollMemory;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.sync.NEStorageUiStateCodec;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeItemTransfer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Coordinates storage state synchronization, server actions, slots, and focused client-side panels. */
public class NEStorageControllerWidget extends NELDLibSyncedStateWidget<NEStorageUiState> {
    public static final int UI_WIDTH = NEStorageLayout.UI_WIDTH;
    public static final int UI_HEIGHT = NEStorageLayout.UI_HEIGHT;

    private static final int ACTION_HUGE_STACK_PAGE = 2;

    private final ECOStorageSystemBlockEntity storage;
    private final Player player;
    private final StoragePageSession pageSession;
    private final NEStorageHeaderPanel headerPanel = new NEStorageHeaderPanel();
    private final NEStorageMetricsPanel metricsPanel = new NEStorageMetricsPanel();
    private final NEStorageUsagePanel usagePanel = new NEStorageUsagePanel();

    public NEStorageControllerWidget(ECOStorageSystemBlockEntity storage, Player player) {
        this(storage, player, new StoragePageSession());
    }

    private NEStorageControllerWidget(
            ECOStorageSystemBlockEntity storage, Player player, StoragePageSession pageSession) {
        super(
                storage.getBlockState().getBlock().getName(),
                UI_WIDTH,
                UI_HEIGHT,
                initialStorageState(storage, pageSession.requestedPage),
                () -> storage.createStorageUiState(pageSession.requestedPage),
                NEStorageUiStateCodec::write,
                NEStorageUiStateCodec::read,
                20);
        this.storage = storage;
        this.player = player;
        this.pageSession = pageSession;
        restoreScrollState();
    }

    private static NEStorageUiState initialStorageState(ECOStorageSystemBlockEntity storage, int requestedPage) {
        NEStorageUiState state = storage.createStorageUiState(requestedPage);
        return state == null ? NEStorageUiState.empty(storage.getBlockPos()) : state;
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new NEAe2IconButtonWidget(
                        PRIORITY_BUTTON_X,
                        PRIORITY_BUTTON_Y,
                        PRIORITY_BUTTON_W,
                        PRIORITY_BUTTON_H,
                        NEAe2IconButtonWidget.Ae2Icon.WRENCH,
                        click -> {
                            if (!click.isRemote && player instanceof ServerPlayer serverPlayer) {
                                MenuOpener.open(PriorityMenu.TYPE, serverPlayer, MenuLocators.forBlockEntity(storage));
                            }
                        })
                .useAeTabButton());
        if (hasInfiniteLayout()) {
            NEPlayerInventoryWidgets.addPlayerInventorySlots(
                    this, player.getInventory(), PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
            addWidget(new SlotWidget(
                            new NEForgeItemTransfer(
                                    storage.getInfiniteComponentItemHandler(), storage::onInfiniteComponentSlotChanged),
                            0,
                            INFINITE_SLOT_X,
                            INFINITE_SLOT_Y,
                            true,
                            true)
                    .setBackgroundTexture(IGuiTexture.EMPTY));
        }
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        restoreScrollState();
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == ACTION_HUGE_STACK_PAGE) {
            int requestedPage = buffer.readVarInt();
            if (isValidHugeStackPageRequest(requestedPage, currentState())) {
                pageSession.requestedPage = requestedPage;
                syncStateNow();
            }
            return;
        }
        super.handleClientAction(id, buffer);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        metricsPanel.drawBackground(graphics, this::absX, this::absY, currentState(), mouseX, mouseY);
        if (hasInfiniteLayout()) {
            NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                    graphics, this::absX, this::absY, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
        }
        usagePanel.drawBackground(graphics, this::absX, this::absY, currentState(), mouseX, mouseY);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        headerPanel.draw(graphics, font(), title, currentState(), this::absX, this::absY);
        if (hasInfiniteLayout()) {
            drawLocalString(
                    graphics,
                    Component.translatable("container.inventory"),
                    PLAYER_INV_X,
                    PLAYER_INV_LABEL_Y,
                    0xFF3F3D52);
        }
        metricsPanel.draw(graphics, font(), this::absX, this::absY, currentState(), mouseX, mouseY);
        usagePanel.drawForeground(graphics, font(), this::absX, this::absY, currentState());
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(PRIORITY_BUTTON_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_W, PRIORITY_BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font(), List.of(GuiText.Priority.text()), mouseX, mouseY);
            return;
        }
        if (headerPanel.drawTooltip(graphics, font(), currentState(), this::absX, this::absY, mouseX, mouseY)) {
            return;
        }
        if (usagePanel.drawTooltip(graphics, font(), this::absX, this::absY, currentState(), mouseX, mouseY)) {
            return;
        }
        metricsPanel.drawTooltip(graphics, font(), this::absX, this::absY, currentState(), mouseX, mouseY);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (usagePanel.mouseWheel(this::absX, this::absY, currentState(), mouseX, mouseY, wheelDelta)) {
            rememberScrollState();
            return true;
        }
        if (metricsPanel.mouseWheel(font(), this::absX, this::absY, currentState(), mouseX, mouseY, wheelDelta)) {
            rememberScrollState();
            return true;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            OptionalInt requestedPage =
                    usagePanel.pageRequestAt(this::absX, this::absY, currentState(), mouseX, mouseY);
            if (requestedPage.isPresent()) {
                writeClientAction(ACTION_HUGE_STACK_PAGE, buf -> buf.writeVarInt(requestedPage.getAsInt()));
                return true;
            }
            if (metricsPanel.mouseClicked(font(), this::absX, this::absY, currentState(), mouseX, mouseY)) {
                rememberScrollState();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && metricsPanel.mouseDragged(currentState(), font(), mouseY)) {
            rememberScrollState();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && metricsPanel.mouseReleased()) {
            rememberScrollState();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean hasInfiniteLayout() {
        return currentState().infiniteSlotVisible();
    }

    private void restoreScrollState() {
        NEStorageScrollMemory.restore(storage, player).ifPresent(snapshot -> {
            metricsPanel.restore(snapshot.leftScrollPixels());
            usagePanel.restore(
                    snapshot.hugeStackPage() == currentState().hugeStackPage()
                            ? snapshot.hugeStackScrollPixels()
                            : 0.0D);
        });
    }

    private void rememberScrollState() {
        NEStorageScrollMemory.remember(
                storage,
                player,
                metricsPanel.scrollPixels(),
                usagePanel.targetScrollPixels(),
                currentState().hugeStackPage());
    }

    static boolean isValidHugeStackPageRequest(int requestedPage, NEStorageUiState state) {
        return requestedPage >= 0 && requestedPage < state.hugeStackPageCount();
    }

    private static final class StoragePageSession {
        private int requestedPage;
    }
}
