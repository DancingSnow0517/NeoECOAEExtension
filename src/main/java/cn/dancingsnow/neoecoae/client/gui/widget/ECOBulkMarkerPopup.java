package cn.dancingsnow.neoecoae.client.gui.widget;

import appeng.api.stacks.AEItemKey;
import appeng.menu.implementations.CellWorkbenchMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOBulkMarkerMenu;
import cn.dancingsnow.neoecoae.storage.bulk.BulkItemKeys;
import cn.dancingsnow.neoecoae.storage.bulk.ECOBulkMarkerActions;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Small AE2-style context menu; the server rechecks the selected key and live disk contents. */
public final class ECOBulkMarkerPopup {
    private static final int ROW_HEIGHT = 20;
    private static final int PADDING = 4;
    private static final int HEIGHT = ROW_HEIGHT * 3 + PADDING * 2;
    private static final Component[] LABELS = {
        Component.translatable("gui.neoecoae.storage.bulk_marker.raise"),
        Component.translatable("gui.neoecoae.storage.bulk_marker.lower"),
        Component.translatable("gui.neoecoae.storage.bulk_marker.remove")
    };
    private final CellWorkbenchMenu menu;
    private int slot = -1;
    private AEItemKey marker;
    private int x, y, width;

    public ECOBulkMarkerPopup(CellWorkbenchMenu menu) { this.menu = menu; }

    public void open(int slot, AEItemKey marker, int mouseX, int mouseY, int screenWidth, int screenHeight, Font font) {
        this.slot = slot;
        this.marker = marker;
        width = 126;
        for (var label : LABELS) width = Math.max(width, font.width(label) + 16);
        x = Math.clamp(mouseX + 2, 0, Math.max(0, screenWidth - width));
        y = Math.clamp(mouseY + 2, 0, Math.max(0, screenHeight - HEIGHT));
    }

    public boolean isOpen() {
        if (slot >= 0 && (!(menu.getWorkbenchItem().getItem() instanceof ECOBulkStorageCellItem)
            || !marker.equals(menu.getHost().getConfig().getKey(slot)))) close();
        return slot >= 0;
    }

    public void close() { slot = -1; marker = null; }

    private boolean enabled(int row) {
        return switch (row) {
            case 0 -> ECOBulkMarkerActions.shift(marker, 1) != null;
            case 1 -> ECOBulkMarkerActions.shift(marker, -1) != null;
            case 2 -> ((ECOBulkMarkerMenu) menu).neoecoae$canRemoveMarker(slot);
            default -> false;
        };
    }

    private int row(double mouseX, double mouseY) {
        if (mouseX < x + PADDING || mouseX >= x + width - PADDING
            || mouseY < y + PADDING || mouseY >= y + HEIGHT - PADDING) return -1;
        return (int) (mouseY - y - PADDING) / ROW_HEIGHT;
    }

    public void click(double mouseX, double mouseY, int button) {
        int row = row(mouseX, mouseY);
        if (button == 0 && row >= 0 && enabled(row)) {
            ((ECOBulkMarkerMenu) menu).neoecoae$editBulkMarker(new ECOBulkMarkerMenu.MarkerEdit(
                slot, BulkItemKeys.write(marker).toString(), row == 0 ? 1 : row == 1 ? -1 : 0));
            close();
        } else if (row < 0 || button != 0) {
            close();
        }
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!isOpen()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        // Same raised gray frame and dark text as the storage-cell action menu.
        graphics.fill(x, y, x + width, y + HEIGHT, 0xFF413F54);
        graphics.fill(x + 1, y + 1, x + width - 1, y + HEIGHT - 1, 0xFFE8E7EF);
        graphics.fill(x + 2, y + 2, x + width - 2, y + HEIGHT - 2, 0xFF8B8A9A);
        graphics.fill(x + 3, y + 3, x + width - 3, y + HEIGHT - 3, 0xFFCACAD4);
        int hovered = row(mouseX, mouseY);
        for (int row = 0; row < LABELS.length; row++) {
            boolean enabled = enabled(row);
            int top = y + PADDING + row * ROW_HEIGHT;
            if (row == hovered && enabled) {
                graphics.fill(x + PADDING, top + 1, x + width - PADDING, top + ROW_HEIGHT - 1, 0x807B81B8);
            }
            int color = !enabled ? 0xFF92919E : row == hovered ? 0xFFFFFFFF : 0xFF413F54;
            graphics.drawString(font, LABELS[row], x + 8, top + (ROW_HEIGHT - font.lineHeight) / 2, color, false);
        }
        if (hovered == 2 && !enabled(2)) {
            graphics.renderTooltip(font, Component.translatable("gui.neoecoae.storage.bulk_marker.occupied"), mouseX, mouseY);
        }
        graphics.pose().popPose();
    }
}
