package cn.dancingsnow.neoecoae.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public class CellHostItemHandler extends SnapshotJournal<ItemStack> implements ResourceHandler<ItemResource> {
    private static final int CELL_SLOT = 0;
    private static final int SLOT_COUNT = 1;

    private final ICellHost host;

    public CellHostItemHandler(ICellHost host) {
        this.host = host;
    }

    @Override
    public int size() {
        return SLOT_COUNT;
    }

    @Override
    public ItemResource getResource(int index) {
        if (!isCellSlot(index)) {
            return ItemResource.EMPTY;
        }
        ItemStack cellStack = host.getCellStack();
        if (cellStack == null || cellStack.isEmpty()) {
            return ItemResource.EMPTY;
        }
        return ItemResource.of(cellStack);
    }

    @Override
    public long getAmountAsLong(int index) {
        if (!isCellSlot(index)) {
            return 0;
        }
        ItemStack cellStack = host.getCellStack();
        return cellStack == null || cellStack.isEmpty() ? 0 : cellStack.getCount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        if (!isCellSlot(index)) {
            return 0;
        }
        return resource.isEmpty() || isValid(index, resource) ? 1 : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return isCellSlot(index) && !resource.isEmpty() && host.isItemValid(resource.toStack());
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

        if (!isCellSlot(index) || amount == 0 || !isValid(index, resource)) {
            return 0;
        }

        ItemStack cellStack = host.getCellStack();
        if (cellStack != null && !cellStack.isEmpty()) {
            return 0;
        }

        updateSnapshots(transaction);
        host.setCellStack(resource.toStack(1));
        return 1;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

        if (!isCellSlot(index) || amount == 0) {
            return 0;
        }

        ItemStack cellStack = host.getCellStack();
        if (cellStack == null || cellStack.isEmpty() || !resource.matches(cellStack)) {
            return 0;
        }

        int extracted = Math.min(amount, cellStack.getCount());
        updateSnapshots(transaction);
        if (extracted == cellStack.getCount()) {
            host.setCellStack(null);
        } else {
            host.setCellStack(cellStack.copyWithCount(cellStack.getCount() - extracted));
        }
        return extracted;
    }

    @Override
    protected ItemStack createSnapshot() {
        ItemStack cellStack = host.getCellStack();
        return cellStack == null ? ItemStack.EMPTY : cellStack.copy();
    }

    @Override
    protected void revertToSnapshot(ItemStack snapshot) {
        host.setCellStack(snapshot.isEmpty() ? null : snapshot.copy());
    }

    private static boolean isCellSlot(int index) {
        return index == CELL_SLOT;
    }
}
