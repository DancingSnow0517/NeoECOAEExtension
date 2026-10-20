package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/** One persistent upgrade card, separate from recipe inputs and speed upgrades. */
final class LargeWorkstationModuleInventory extends ItemStackHandler {
    private final Runnable changed;

    LargeWorkstationModuleInventory(Runnable changed) {
        super(1);
        this.changed = changed;
    }

    @Override
    public int getSlotLimit(int slot) { return 1; }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) { return LargeWorkstationModule.isUpgradeCard(stack); }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (!stack.isEmpty() && (!isItemValid(slot, stack) || stack.getCount() > 1)) {
            throw new IllegalArgumentException("A workstation module slot holds one upgrade card");
        }
        super.setStackInSlot(slot, stack);
    }

    @Override
    protected void onContentsChanged(int slot) { changed.run(); }
}
