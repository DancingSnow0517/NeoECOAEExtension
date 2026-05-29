package cn.dancingsnow.neoecoae.gui.nativeui.slot;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Slot that directly binds an AE2 InternalInventory without going through
 * an IItemHandler wrapper. Avoids client/server sync instability caused
 * by the ItemHandler→InternalInventory translation layer.
 */
public class NEInternalInventorySlot extends Slot {

    private final InternalInventory inv;
    private final int index;
    private final ECOIntegratedWorkingStationBlockEntity owner;
    private final boolean allowPlace;
    private final boolean allowPickup;

    public NEInternalInventorySlot(InternalInventory inv, int index,
                                    int x, int y, ECOIntegratedWorkingStationBlockEntity owner,
                                    boolean allowPlace, boolean allowPickup) {
        super(null, index, x, y);
        this.inv = inv;
        this.index = index;
        this.owner = owner;
        this.allowPlace = allowPlace;
        this.allowPickup = allowPickup;
    }

    @Override
    public @NotNull ItemStack getItem() {
        return inv.getStackInSlot(index);
    }

    @Override
    public void set(@NotNull ItemStack stack) {
        inv.setItemDirect(index, stack.copy());
        setChanged();
    }

    @Override
    public @NotNull ItemStack remove(int amount) {
        ItemStack current = inv.getStackInSlot(index);
        if (current.isEmpty()) return ItemStack.EMPTY;
        int toRemove = Math.min(amount, current.getCount());
        ItemStack result = current.copyWithCount(toRemove);
        current.shrink(toRemove);
        inv.setItemDirect(index, current);
        setChanged();
        return result;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return allowPlace;
    }

    @Override
    public boolean mayPickup(@NotNull Player player) {
        return allowPickup;
    }

    @Override
    public void setChanged() {
        // Do NOT call super.setChanged() — this.container is null.
        if (owner != null) {
            owner.onGuiInventoryChanged();
        }
    }

    @Override
    public boolean allowModification(@NotNull Player player) {
        // this.container is null, so return true directly
        return allowPickup || allowPlace;
    }

    @Override
    public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
        // No container to notify; just mark changed
        setChanged();
    }
}
