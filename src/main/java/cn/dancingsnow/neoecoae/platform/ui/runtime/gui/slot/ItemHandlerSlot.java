package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.slot;
import net.minecraftforge.items.IItemHandler;
public class ItemHandlerSlot extends net.minecraftforge.items.SlotItemHandler{
    private java.util.function.Predicate<net.minecraft.world.entity.player.Player> canTake=player->true;
    public ItemHandlerSlot(IItemHandler handler,int index){super(handler,index,0,0);}
    public ItemHandlerSlot setCanTake(java.util.function.Predicate<net.minecraft.world.entity.player.Player> predicate){canTake=java.util.Objects.requireNonNull(predicate);return this;}
    @Override public boolean mayPickup(net.minecraft.world.entity.player.Player player){return canTake.test(player)&&super.mayPickup(player);}
}
