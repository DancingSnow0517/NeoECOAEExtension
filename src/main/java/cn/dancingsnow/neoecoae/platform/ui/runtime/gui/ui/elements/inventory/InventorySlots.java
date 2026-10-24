package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.slot.LocalSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.FlexDirection;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class InventorySlots extends UIElement {
    private final Map<ItemSlot, Slot> automaticBindings = new IdentityHashMap<>();
    public static class Row extends UIElement {
        public final ItemSlot[] slots=new ItemSlot[9];
        public Row(){layout(l->l.flexDirection(FlexDirection.ROW));for(int i=0;i<slots.length;i++){slots[i]=new ItemSlot().slotStyle(s->s.isPlayerSlot(true));addChild(slots[i]);}}
        public Row apply(Consumer<ItemSlot> action){for(var slot:slots)action.accept(slot);return this;}
    }
    public final Row[] rows={new Row(),new Row(),new Row()};
    public final Row hotbar=new Row();
    public final UIElement mainInventory=new UIElement().addClass("__inventory_main__");
    public InventorySlots(){for(var row:rows)mainInventory.addChild(row);hotbar.addClass("__inventory_hotbar__");hotbar.layout(l->l.marginTop(4));addChildren(mainInventory,hotbar);}
    @Override
    protected void onModularUIChanged(UIEvent event) {
        super.onModularUIChanged(event);
        var ui = getModularUI();
        if (ui == null || ui.player == null || event.customData == ui) return;
        // Bind before LDLib1 collects native menu slots; no client singleton is needed on the server.
        var inventory = ui.player.getInventory();
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].slots.length; column++) {
                bindPlayerSlot(rows[row].slots[column], inventory, row * 9 + column + 9);
            }
        }
        for (int index = 0; index < hotbar.slots.length; index++) {
            bindPlayerSlot(hotbar.slots[index], inventory, index);
        }
    }

    private void bindPlayerSlot(ItemSlot element, Inventory inventory, int index) {
        var current = element.getSlot();
        // Explicit bindings can carry restrictions such as the held workstation's locked slot.
        if (!(current instanceof LocalSlot) && current != automaticBindings.get(element)) return;
        var slot = new Slot(inventory, index, 0, 0);
        element.bind(slot);
        automaticBindings.put(element, slot);
    }

    public InventorySlots bind(Inventory inventory){for(int r=0;r<rows.length;r++)for(int c=0;c<9;c++)rows[r].slots[c].bind(new Slot(inventory,r*9+c+9,0,0));for(int i=0;i<9;i++)hotbar.slots[i].bind(new Slot(inventory,i,0,0));return this;}
    public InventorySlots apply(Consumer<ItemSlot> action){for(var row:rows)for(var slot:row.slots)action.accept(slot);for(var slot:hotbar.slots)action.accept(slot);return this;}
}
