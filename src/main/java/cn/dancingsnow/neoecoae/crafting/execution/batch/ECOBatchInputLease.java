package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import java.util.List;

/** One-shot ownership lease for the physical inputs of one current batch. */
public final class ECOBatchInputLease {
    private final ListCraftingInventory inventory;
    private final List<GenericStack> inputs;
    private boolean settled;

    private ECOBatchInputLease(ListCraftingInventory inventory, List<GenericStack> inputs) {
        this.inventory = inventory;
        this.inputs = List.copyOf(inputs);
    }

    public static ECOBatchInputLease acquire(ListCraftingInventory inventory, List<GenericStack> inputs) {
        return ECOBatchCraftingHelper.extractExact(inventory, inputs) ? new ECOBatchInputLease(inventory, inputs) : null;
    }

    public void commit() {
        if (settled) throw new IllegalStateException("Batch input lease already settled");
        settled = true;
    }

    public void transferOwnership() {
        commit();
    }

    public void rollback() {
        if (!settled) {
            settled = true;
            ECOBatchCraftingHelper.insertAll(inventory, inputs);
        }
    }
}
