package cn.dancingsnow.neoecoae.gui.common;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.inventory.ClickType;

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
            var mui = root.getModularUI();
            if (!event.isShiftDown() || !root.isMouseDown(button) || !mui.getMenu().getCarried().isEmpty()) {
                button = -1;
                return;
            }
            float deltaX = event.x - lastX;
            float deltaY = event.y - lastY;
            int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(deltaX), Math.abs(deltaY)) / 4));
            for (int step = 1; step <= steps; step++) {
                visit(mui.hitTestAtScreen(lastX + deltaX * step / steps, lastY + deltaY * step / steps));
            }
            lastX = event.x;
            lastY = event.y;
        }, true);
    }

    public void addSlot(ItemSlot slot, Runnable action) {
        actions.put(slot, action);
    }

    public void addMenuSlot(ItemSlot slot, int dragButton) {
        addSlot(slot, () -> slot.sendMessage(MESSAGE));
        slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == dragButton) begin(slot, event);
        }, true);
        slot.onMessage(MESSAGE, ignored -> {
            var mui = slot.getModularUI();
            if (mui == null || mui.player == null || mui.player.level().isClientSide) return;
            var player = mui.player;
            var menu = mui.getMenu();
            var source = slot.getSlot();
            if (menu == player.containerMenu && menu.stillValid(player) && menu.getCarried().isEmpty()
                && source.index >= 0 && source.index < menu.slots.size() && menu.getSlot(source.index) == source) {
                menu.clicked(source.index, dragButton, ClickType.QUICK_MOVE, player);
            }
        });
    }

    public boolean begin(ItemSlot slot, UIEvent event) {
        var mui = slot.getModularUI();
        if (!event.isShiftDown() || mui == null || mui.getMenu() == null || !mui.getMenu().getCarried().isEmpty()) {
            return false;
        }
        visited.clear();
        visited.add(slot);
        lastX = event.x;
        lastY = event.y;
        button = event.button;
        return true;
    }

    private void visit(UIElement hit) {
        while (hit != null && !actions.containsKey(hit)) hit = hit.getParent();
        if (hit instanceof ItemSlot slot && !slot.getValue().isEmpty() && visited.add(slot)) {
            actions.get(slot).run();
        }
    }
}
