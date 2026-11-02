package cn.dancingsnow.neoecoae.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

public class CellHostItemHandler extends ItemStackResourceHandler {


    private final ICellHost host;

    public CellHostItemHandler(ICellHost host) {
        this.host = host;
    }

    @Override
    protected @NonNull ItemStack getStack() {
        ItemStack stack = host.getCellStack();
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    @Override
    protected void setStack(@NonNull ItemStack stack) {
        host.setCellStack(stack.isEmpty() ? null : stack);
    }

    @Override
    protected boolean isValid(ItemResource resource) {
        return host.canExtractCell() && host.isItemValid(resource.toStack());
    }

    @Override
    protected int getCapacity(ItemResource resource) {
        return 1;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return host.canExtractCell() ? super.extract(index, resource, amount, transaction) : 0;
    }
}
