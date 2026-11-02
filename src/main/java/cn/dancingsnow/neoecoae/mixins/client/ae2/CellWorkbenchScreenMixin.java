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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        int button = event.button();
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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (neoecoae$consumeMarkerRelease || neoecoae$markerPopup.isOpen()) {
            neoecoae$consumeMarkerRelease = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return neoecoae$consumeMarkerRelease || neoecoae$markerPopup.isOpen()
            || super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (neoecoae$markerPopup.isOpen()) {
            if (event.isEscape() || minecraft.options.keyInventory.matches(event)) neoecoae$markerPopup.close();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        neoecoae$markerPopup.render(graphics, font, mouseX, mouseY);
    }
}
