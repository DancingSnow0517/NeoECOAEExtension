package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.inventory.ClickType;

/** Shift-drag routing for native menu slots and host-owned display slots. */
public final class ShiftQuickMove {
    private static final String MESSAGE = "neoecoae:shift_quick_move";
    private final Map<ItemSlot, Runnable> actions = new HashMap<>();
    private final Set<ItemSlot> visited = new HashSet<>();
    private int button = -1;
    private float lastX;
    private float lastY;

    public void attachRoot(UIElement root) {
        root.addEventListener(UIEvents.MOUSE_DOWN, event -> button = -1, true);
        root.addEventListener(UIEvents.MOUSE_UP, event -> button = -1, true);
        root.addEventListener(UIEvents.MOUSE_MOVE, event -> {
            if (button == -1) return;
            var modularUI = root.getModularUI();
            if (!event.isShiftDown() || modularUI == null || !root.isMouseDown(button)
                || !modularUI.getMenu().getCarried().isEmpty()) {
                button = -1;
                return;
            }
            float dx = event.x - lastX;
            float dy = event.y - lastY;
            int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 4));
            for (int i = 1; i <= steps; i++) {
                visit(modularUI.hitTestAtScreen(lastX + dx * i / steps, lastY + dy * i / steps));
            }
            lastX = event.x;
            lastY = event.y;
        }, true);
    }

    public void addMenuSlot(ItemSlot slot) {
        addMenuSlot(slot, 0);
    }

    public void addMenuSlot(ItemSlot slot, int dragButton) {
        addSlot(slot, () -> {
            var modularUI = slot.getModularUI();
            var player = modularUI == null ? null : modularUI.player;
            var menu = modularUI == null ? null : modularUI.getMenu();
            if (player != null && menu == player.containerMenu && menu.stillValid(player)
                && menu.getCarried().isEmpty()) {
                menu.clicked(slot.getSlot().index, dragButton, ClickType.QUICK_MOVE, player);
            }
        });
        slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == dragButton) begin(slot, event);
        }, true);
    }

    public void addSlot(ItemSlot slot, Runnable action) {
        actions.put(slot, action);
        slot.onMessage(MESSAGE, ignored -> actions.get(slot).run());
    }

    public boolean begin(ItemSlot slot, UIEvent event) {
        visited.clear();
        var modularUI = slot.getModularUI();
        var menu = modularUI == null ? null : modularUI.getMenu();
        if (!event.isShiftDown() || menu == null || !menu.getCarried().isEmpty()) return false;
        visited.add(slot);
        lastX = event.x;
        lastY = event.y;
        button = event.button;
        return true;
    }

    private void visit(UIElement hit) {
        while (hit != null && !actions.containsKey(hit)) hit = hit.getParentElement();
        if (hit instanceof ItemSlot slot && !slot.getValue().isEmpty() && visited.add(slot)) {
            actions.get(slot).run();
        }
    }
}
