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
        // The press itself stays a vanilla Shift-click; only the slots crossed afterwards are ours.
        shiftDraggedSlots.clear();
        Slot slot = button == 0 && hasShiftDown() && menu.getCarried().isEmpty() ? slotAt(mouseX, mouseY) : null;
        shiftDragging = slot != null;
        if (slot != null) shiftDraggedSlots.add(slot.index);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && shiftDragging && hasShiftDown()) {
            // One drag event per frame: a fast swipe jumps over whole slots, so walk the segment.
            int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dragX), Math.abs(dragY)) / 4));
            for (int i = steps - 1; i >= 0; i--) {
                quickMove(slotAt(mouseX - dragX * i / steps, mouseY - dragY * i / steps));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) shiftDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Vanilla's private findSlot: screen coordinates, active slots only. */
    private Slot slotAt(double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            if (isHovering(slot, mouseX, mouseY) && slot.isActive()) return slot;
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
