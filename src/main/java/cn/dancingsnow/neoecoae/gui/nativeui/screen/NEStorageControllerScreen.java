package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStorageControllerMenu;
import cn.dancingsnow.neoecoae.network.NEStorageUiState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Screen for the ECO Storage Controller with live read-only status.
 * <p>
 * Primary display path: S2C {@link NEStorageUiState} pushed from the server
 * menu tick. Falls back to client BE getters if no state has been received yet.
 * </p>
 */
public class NEStorageControllerScreen extends NEBaseMachineScreen<NEStorageControllerMenu> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private NEStorageUiState storageState;

    public NEStorageControllerScreen(NEStorageControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.STORAGE_CONTROLLER);
        this.imageWidth = 280;
        this.imageHeight = 150;
        this.storageState = NEStorageUiState.empty(menu.getMachinePos());
    }

    /** Called from {@link cn.dancingsnow.neoecoae.network.NENetwork} on the client thread. */
    public void setStorageUiState(NEStorageUiState state) {
        this.storageState = state;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        NEStorageUiState s = this.storageState;

        // Fallback: if no S2C state received yet, read from client BE
        if (s.totalTypes() == 0 && s.totalBytes() == 0 && !s.formed()) {
            ECOStorageSystemBlockEntity be = getStorageBE();
            if (be != null) {
                s = new NEStorageUiState(
                    menu.getMachinePos(),
                    be.getTotalUsedTypes(), be.getTotalTypes(),
                    be.getTotalUsedBytes(), be.getTotalBytes(),
                    be.getStoredEnergy(), be.getMaxEnergy(),
                    be.isFormed()
                );
            }
        }

        int y = 56;
        final int x = NENativeUiConstants.TITLE_X;
        final int color = 0xFFC0C0D0;
        final int labelColor = 0xFF8A8AA0;

        // Types
        guiGraphics.drawString(font,
            Component.literal("Types: " + fmt(s.usedTypes()) + " / " + fmt(s.totalTypes())),
            x, y, color);
        y += 14;

        // Bytes
        guiGraphics.drawString(font,
            Component.literal("Bytes: " + fmt(s.usedBytes()) + " / " + fmt(s.totalBytes())),
            x, y, color);
        y += 14;

        // Energy
        guiGraphics.drawString(font,
            Component.literal("Energy: " + fmt(s.storedEnergy()) + " / " + fmt(s.maxEnergy()) + " AE"),
            x, y, color);
        y += 14;

        // Formed
        guiGraphics.drawString(font,
            Component.literal("Formed: " + s.formed()),
            x, y, labelColor);
    }

    private ECOStorageSystemBlockEntity getStorageBE() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(menu.getMachinePos());
        if (be instanceof ECOStorageSystemBlockEntity storage) {
            return storage;
        }
        return null;
    }

    private static String fmt(long value) {
        return NUMBER_FORMAT.format(value);
    }

    public NEStorageControllerMenu getMenu() {
        return menu;
    }
}
