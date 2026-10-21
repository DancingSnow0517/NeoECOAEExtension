package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.FlexDirection;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import java.util.function.Consumer;

public class InventorySlots extends UIElement {
    public static class Row extends UIElement {
        public final ItemSlot[] slots=new ItemSlot[9];
        public Row(){layout(l->l.flexDirection(FlexDirection.ROW));for(int i=0;i<slots.length;i++){slots[i]=new ItemSlot().slotStyle(s->s.isPlayerSlot(true));addChild(slots[i]);}}
        public Row apply(Consumer<ItemSlot> action){for(var slot:slots)action.accept(slot);return this;}
    }
    public final Row[] rows={new Row(),new Row(),new Row()};
    public final Row hotbar=new Row();
    public final UIElement mainInventory=new UIElement().addClass("__inventory_main__");
    public InventorySlots(){for(var row:rows)mainInventory.addChild(row);hotbar.addClass("__inventory_hotbar__");hotbar.layout(l->l.marginTop(4));addChildren(mainInventory,hotbar);}
    public InventorySlots bind(Inventory inventory){for(int r=0;r<rows.length;r++)for(int c=0;c<9;c++)rows[r].slots[c].bind(new Slot(inventory,r*9+c+9,0,0));for(int i=0;i<9;i++)hotbar.slots[i].bind(new Slot(inventory,i,0,0));return this;}
    public InventorySlots apply(Consumer<ItemSlot> action){for(var row:rows)for(var slot:row.slots)action.accept(slot);for(var slot:hotbar.slots)action.accept(slot);return this;}
}
