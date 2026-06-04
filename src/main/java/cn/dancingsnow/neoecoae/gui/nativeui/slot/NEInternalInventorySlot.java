package cn.dancingsnow.neoecoae.gui.nativeui.slot;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Slot that directly binds an AE2 {@link InternalInventory} without going through
 * an IItemHandler wrapper.
 * <p>
 * Important: this slot must never pass a null Container to the vanilla Slot
 * constructor. Vanilla slot interaction code expects a real Container and may
 * call into it while placing, removing, quick-moving or synchronising stacks.
 * </p>
 */
public class NEInternalInventorySlot extends Slot {

    private final boolean allowPlace;
    private final boolean allowPickup;

    public NEInternalInventorySlot(
            InternalInventory inv,
            int index,
            int x,
            int y,
            ECOIntegratedWorkingStationBlockEntity owner,
            boolean allowPlace,
            boolean allowPickup) {
        super(new InternalInventoryContainer(inv, owner), index, x, y);
        this.allowPlace = allowPlace;
        this.allowPickup = allowPickup;
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
    public boolean allowModification(@NotNull Player player) {
        return allowPickup || allowPlace;
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public int getMaxStackSize(@NotNull ItemStack stack) {
        return Math.min(64, stack.getMaxStackSize());
    }

    /**
     * Minimal Container bridge for AE2 InternalInventory.
     * <p>
     * The bridge lets vanilla Slot code use the normal Container contract while
     * reads/writes still go directly to AE2's InternalInventory. Every mutation
     * notifies the owner so recipe matching and menu synchronisation can refresh.
     * </p>
     */
    private static final class InternalInventoryContainer implements Container {
        private final InternalInventory inv;
        private final ECOIntegratedWorkingStationBlockEntity owner;

        private InternalInventoryContainer(InternalInventory inv, ECOIntegratedWorkingStationBlockEntity owner) {
            this.inv = inv;
            this.owner = owner;
        }

        @Override
        public int getContainerSize() {
            return inv.size();
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < inv.size(); i++) {
                if (!inv.getStackInSlot(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public @NotNull ItemStack getItem(int slot) {
            return inv.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack removeItem(int slot, int amount) {
            ItemStack extracted = inv.extractItem(slot, amount, false);
            if (!extracted.isEmpty()) {
                setChanged();
            }
            return extracted;
        }

        @Override
        public @NotNull ItemStack removeItemNoUpdate(int slot) {
            ItemStack current = inv.getStackInSlot(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack result = current.copy();
            inv.setItemDirect(slot, ItemStack.EMPTY);
            setChanged();
            return result;
        }

        @Override
        public void setItem(int slot, @NotNull ItemStack stack) {
            inv.setItemDirect(slot, stack.copy());
            setChanged();
        }

        @Override
        public void setChanged() {
            if (owner != null) {
                owner.onGuiInventoryChanged();
            }
        }

        @Override
        public boolean stillValid(@NotNull Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            boolean changed = false;
            for (int i = 0; i < inv.size(); i++) {
                if (!inv.getStackInSlot(i).isEmpty()) {
                    inv.setItemDirect(i, ItemStack.EMPTY);
                    changed = true;
                }
            }
            if (changed) {
                setChanged();
            }
        }
    }
}
