package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.implementations.CellWorkbenchScreen;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.implementations.CellWorkbenchMenu;
import cn.dancingsnow.neoecoae.client.NeoECOAEClient;
import cn.dancingsnow.neoecoae.client.gui.widget.ECOBulkMarkerPopup;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import appeng.api.stacks.AEItemKey;
import appeng.menu.slot.CellPartitionSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CellWorkbenchScreen.class)
public abstract class CellWorkbenchScreenMixin extends UpgradeableScreen<CellWorkbenchMenu> {
    @Unique
    private Button neoecoae$importJeiBookmarks;
    @Unique private ECOBulkMarkerPopup neoecoae$markerPopup;
    @Unique private boolean neoecoae$consumeMarkerRelease;

    protected CellWorkbenchScreenMixin(CellWorkbenchMenu menu, Inventory playerInventory,
                                       Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void init() {
        neoecoae$markerPopup = new ECOBulkMarkerPopup(menu);
        if (neoecoae$importJeiBookmarks == null) {
            neoecoae$importJeiBookmarks = addToLeftToolbar(
                NeoECOAEClient.createJeiBookmarkButton(menu));
        }
        super.init();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (neoecoae$markerPopup.isOpen()) {
            neoecoae$markerPopup.click(x, y, button);
            neoecoae$consumeMarkerRelease = true;
            return true;
        }
        if (button == 1 && menu.getCarried().isEmpty()
            && menu.getWorkbenchItem().getItem() instanceof ECOBulkStorageCellItem) {
            for (var slot : menu.slots) {
                if (slot instanceof CellPartitionSlot partition && partition.isSlotEnabled() && isHovering(slot, x, y)
                    && menu.getHost().getConfig().getKey(slot.getContainerSlot()) instanceof AEItemKey marker) {
                    neoecoae$markerPopup.open(slot.getContainerSlot(), marker, (int) x, (int) y, width, height, font);
                    neoecoae$consumeMarkerRelease = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (neoecoae$consumeMarkerRelease || neoecoae$markerPopup.isOpen()) {
            neoecoae$consumeMarkerRelease = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        return neoecoae$consumeMarkerRelease || neoecoae$markerPopup.isOpen()
            || super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (neoecoae$markerPopup.isOpen()) {
            if (key == 256 || minecraft.options.keyInventory.matches(key, scanCode)) neoecoae$markerPopup.close();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        neoecoae$markerPopup.render(graphics, font, mouseX, mouseY);
    }
}
