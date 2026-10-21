package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.slot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
/** Display-only slot; no real inventory transfer is authorized by its presence. */
public class LocalSlot extends Slot{
    public LocalSlot(){super(new SimpleContainer(1),0,0,0);}
    @Override public boolean mayPlace(ItemStack stack){return false;}
    @Override public boolean mayPickup(Player player){return false;}
    @Override public ItemStack remove(int amount){return ItemStack.EMPTY;}
}
