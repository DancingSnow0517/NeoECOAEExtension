package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One attempt's physical input ownership. A lease can be settled only once. */
public final class ECOBatchInputLease {
    private final ListCraftingInventory inventory;
    private final List<GenericStack> inputs;
    private final Map<AEKey, BigInteger> exactInputs;
    private boolean settled;

    private ECOBatchInputLease(
            ListCraftingInventory inventory, List<GenericStack> inputs, Map<AEKey, BigInteger> exactInputs) {
        this.inventory = inventory;
        this.inputs = List.copyOf(inputs);
        this.exactInputs = Map.copyOf(exactInputs);
    }

    public static ECOBatchInputLease acquire(
            ListCraftingInventory inventory, List<GenericStack> inputs, Map<AEKey, BigInteger> exactInputs) {
        if (!exactInputs.isEmpty()) {
            if (!(inventory instanceof ECOExactInventory exact) || !exact.isEnabled() || !exact.debit(exactInputs))
                return null;
            return new ECOBatchInputLease(inventory, List.of(), exactInputs);
        }
        return ECOBatchCraftingHelper.tryExtractExact(inventory, inputs)
                ? new ECOBatchInputLease(inventory, inputs, Map.of())
                : null;
    }

    public void commit() {
        ensureOpen();
        settled = true;
    }

    public void commitLinear(long accepted, long offered) {
        ensureOpen();
        if (offered <= 0L || accepted <= 0L || accepted > offered || !exactInputs.isEmpty()) {
            throw new IllegalArgumentException("Invalid linear batch settlement");
        }
        List<GenericStack> refund = new ArrayList<>();
        for (GenericStack stack : inputs) {
            if (stack.amount() % offered != 0L) throw new IllegalArgumentException("Nonlinear batch input");
            long count = Math.multiplyExact(stack.amount() / offered, offered - accepted);
            if (count > 0L) refund.add(new GenericStack(stack.what(), count));
        }
        settled = true;
        ECOBatchCraftingHelper.insertAll(inventory, refund);
    }

    public void rollback() {
        if (settled) return;
        settled = true;
        if (exactInputs.isEmpty()) ECOBatchCraftingHelper.insertAll(inventory, inputs);
        else ((ECOExactInventory) inventory).restore(exactInputs);
    }

    private void ensureOpen() {
        if (settled) throw new IllegalStateException("Batch input lease already settled");
    }
}
