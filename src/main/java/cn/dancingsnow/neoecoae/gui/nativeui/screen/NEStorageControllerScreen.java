package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStorageControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Screen for the ECO Storage Controller with live read-only status.
 */
public class NEStorageControllerScreen extends NEBaseMachineScreen<NEStorageControllerMenu> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final Logger LOG = LoggerFactory.getLogger(NENativeUiConstants.LOGGER_NAME);

    public NEStorageControllerScreen(NEStorageControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.STORAGE_CONTROLLER);
        this.imageWidth = 280;
        this.imageHeight = 150;
    }

    @Override
    protected void init() {
        super.init();
        ECOStorageSystemBlockEntity be = getStorageBE();
        if (be != null) {
            LOG.info("[NeoECOAE] Storage UI opened — client BE at {}:" +
                " types={}/{} bytes={}/{} energy={}/{} formed={}",
                menu.getMachinePos(),
                be.getTotalUsedTypes(), be.getTotalTypes(),
                be.getTotalUsedBytes(), be.getTotalBytes(),
                be.getStoredEnergy(), be.getMaxEnergy(),
                be.isFormed());
            // Raw array dump for debugging
            System.out.println("[NeoECOAE DEBUG] Storage totalTypes=" + be.getTotalTypes() +
                " totalBytes=" + be.getTotalBytes() +
                " storedEnergy=" + be.getStoredEnergy() +
                " maxEnergy=" + be.getMaxEnergy() +
                " formed=" + be.isFormed());
        } else {
            LOG.info("[NeoECOAE] Storage UI opened — client BE not found at {}", menu.getMachinePos());
        }
    }

    @Override
    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ECOStorageSystemBlockEntity be = getStorageBE();
        if (be == null) {
            return;
        }

        int y = 56;
        final int x = NENativeUiConstants.TITLE_X;
        final int color = 0xFFC0C0D0;
        final int labelColor = 0xFF8A8AA0;

        // Types
        guiGraphics.drawString(font,
            Component.literal("Types: " + fmt(be.getTotalUsedTypes()) + " / " + fmt(be.getTotalTypes())),
            x, y, color);
        y += 14;

        // Bytes
        guiGraphics.drawString(font,
            Component.literal("Bytes: " + fmt(be.getTotalUsedBytes()) + " / " + fmt(be.getTotalBytes())),
            x, y, color);
        y += 14;

        // Energy
        guiGraphics.drawString(font,
            Component.literal("Energy: " + fmt(be.getStoredEnergy()) + " / " + fmt(be.getMaxEnergy()) + " AE"),
            x, y, color);
        y += 14;

        // Formed
        guiGraphics.drawString(font,
            Component.literal("Formed: " + be.isFormed()),
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
}
