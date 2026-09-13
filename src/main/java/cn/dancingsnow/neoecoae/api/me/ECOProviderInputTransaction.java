package cn.dancingsnow.neoecoae.api.me;

import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Exact CPU-input ownership transfer shared by non-Fastpath provider batches. */
final class ECOProviderInputTransaction {
    private final ListCraftingInventory inventory;
    private final List<GenericStack> inputs;
    private boolean settled;

    private ECOProviderInputTransaction(ListCraftingInventory inventory, List<GenericStack> inputs) {
        this.inventory = inventory;
        this.inputs = List.copyOf(inputs);
    }

    @Nullable
    static ECOProviderInputTransaction begin(ListCraftingInventory inventory, List<GenericStack> inputs) {
        return ECOBatchCraftingHelper.extractExact(inventory, inputs)
                ? new ECOProviderInputTransaction(inventory, inputs) : null;
    }

    void transferOwnership() {
        settled = true;
    }

    void rollback() {
        if (!settled) {
            settled = true;
            ECOBatchCraftingHelper.insertAll(inventory, inputs);
        }
    }
}
