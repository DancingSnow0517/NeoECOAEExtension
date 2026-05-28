package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStorageControllerMenu;
import cn.dancingsnow.neoecoae.network.NEStorageUiState;
import cn.dancingsnow.neoecoae.network.NEStorageUiTypeState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Screen for the ECO Storage Controller with live read-only status.
 * <p>
 * Primary display path: S2C {@link NEStorageUiState} pushed from the server
 * menu tick. Storage capacity is shown per cell type (Items, Fluids, etc.).
 * Before the first packet arrives the screen shows a brief fallback read from
 * the client-side BE (opening-time snapshot, not live).
 * </p>
 */
public class NEStorageControllerScreen extends NEBaseMachineScreen<NEStorageControllerMenu> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private boolean hasStorageState;
    private NEStorageUiState storageState;

    public NEStorageControllerScreen(NEStorageControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.STORAGE_CONTROLLER);
        this.imageWidth = 320;
        this.imageHeight = 220;
        this.storageState = NEStorageUiState.empty(menu.getMachinePos());
    }

    /** Called from the network thread via {@link cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers}. */
    public void setStorageUiState(NEStorageUiState state) {
        this.hasStorageState = true;
        this.storageState = state;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected boolean shouldShowCraftingEntryButton() {
        return true;
    }

    @Override
    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        NEStorageUiState s;

        if (hasStorageState) {
            s = this.storageState;
        } else {
            // Opening-time fallback: read client BE once while waiting for the
            // first S2C packet. Not used for live refresh.
            ECOStorageSystemBlockEntity be = getStorageBE();
            if (be != null) {
                // Wrap legacy BE getters into a single "unknown" type row
                NEStorageUiTypeState fallbackType = new NEStorageUiTypeState(
                    ResourceLocation.fromNamespaceAndPath("neoecoae", "legacy"),
                    "Storage",
                    be.getTotalUsedTypes(), be.getTotalTypes(),
                    be.getTotalUsedBytes(), be.getTotalBytes()
                );
                s = new NEStorageUiState(
                    menu.getMachinePos(),
                    Collections.singletonList(fallbackType),
                    be.getStoredEnergy(), be.getMaxEnergy(),
                    be.isFormed()
                );
            } else {
                s = this.storageState;
            }
        }

        final int x = NENativeUiConstants.TITLE_X;
        final int labelColor = NENativeUiConstants.PANEL_TEXT_MUTED;
        final int valueColor = NENativeUiConstants.PANEL_TEXT_SECONDARY;
        int y = 50;

        // Per-cell-type rows
        List<NEStorageUiTypeState> types = s.typeStates();
        if (types.isEmpty()) {
            guiGraphics.drawString(font,
                Component.translatable("gui.neoecoae.machine.no_storage_cells"),
                x, y, labelColor);
            y += 14;
        } else {
            for (NEStorageUiTypeState ts : types) {
                guiGraphics.drawString(font,
                    Component.literal(ts.displayName() + ":"),
                    x, y, valueColor);
                y += 12;
                guiGraphics.drawString(font,
                    Component.translatable("gui.neoecoae.machine.types_value", fmt(ts.usedTypes()), fmt(ts.totalTypes())),
                    x + 4, y, valueColor);
                y += 11;
                guiGraphics.drawString(font,
                    Component.translatable("gui.neoecoae.machine.bytes_value", fmt(ts.usedBytes()), fmt(ts.totalBytes())),
                    x + 4, y, valueColor);
                y += 13;
            }
        }

        // Energy
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.energy_value", fmt(s.storedEnergy()), fmt(s.maxEnergy())),
            x, y, valueColor);
        y += 14;

        // Formed
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.formed").append(": ").append(boolText(s.formed())),
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
