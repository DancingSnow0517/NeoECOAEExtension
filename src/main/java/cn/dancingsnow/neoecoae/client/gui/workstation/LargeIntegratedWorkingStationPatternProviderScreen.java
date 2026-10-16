package cn.dancingsnow.neoecoae.client.gui.workstation;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import java.util.HashSet;
import java.util.Set;

/** The copied ExtendedAE pattern-provider screen with NeoECOAE-owned resources. */
public class LargeIntegratedWorkingStationPatternProviderScreen
    extends PatternProviderScreen<LargeIntegratedWorkingStationPatternProviderMenu> {

    private final Set<Integer> shiftDraggedSlots = new HashSet<>();
    private boolean shiftDragging;

    public LargeIntegratedWorkingStationPatternProviderScreen(
        LargeIntegratedWorkingStationPatternProviderMenu menu,
        Inventory playerInventory,
        Component title,
        ScreenStyle style
    ) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        // EAEP adds these private widgets to the superclass, including after a resize.
        hideSmartDoublingWidget("eap$SmartDoublingToggle");
        hideSmartDoublingWidget("eap$PerProviderLimitInput");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hasShiftDown()) {
            Slot slot = slotAt(mouseX, mouseY);
            if (slot != null) {
                shiftDragging = true;
                quickMove(slot);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && shiftDragging && hasShiftDown()) {
            quickMove(slotAt(mouseX, mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            shiftDragging = false;
            shiftDraggedSlots.clear();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private Slot slotAt(double mouseX, double mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        for (Slot slot : menu.slots) {
            if (isHovering(slot, localX, localY)) return slot;
        }
        return null;
    }

    private void quickMove(Slot slot) {
        if (slot == null || !shiftDraggedSlots.add(slot.index)) return;
        slotClicked(slot, slot.index, 0, ClickType.QUICK_MOVE);
    }

    private void hideSmartDoublingWidget(String name) {
        try {
            var field = PatternProviderScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            if (field.get(this) instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.visible = false;
                widget.active = false;
                widget.setFocused(false);
                removeWidget(widget);
            }
        } catch (NoSuchFieldException absent) {
            // EAEP is optional.
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot hide workstation smart doubling", failure);
        }
    }
}
