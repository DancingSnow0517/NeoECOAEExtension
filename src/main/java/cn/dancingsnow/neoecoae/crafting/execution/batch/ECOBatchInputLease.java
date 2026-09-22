package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import java.util.List;
import java.math.BigInteger;
import java.util.Map;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;

/** One-shot ownership lease for the physical inputs of one current batch. */
public final class ECOBatchInputLease {
    private final ListCraftingInventory inventory;
    private final List<GenericStack> inputs;
    private final Map<AEKey, BigInteger> exactInputs;
    private boolean settled;

    private ECOBatchInputLease(ListCraftingInventory inventory, List<GenericStack> inputs) {
        this.inventory = inventory;
        this.inputs = List.copyOf(inputs);
        this.exactInputs = Map.of();
    }

    private ECOBatchInputLease(ECOExactInventory inventory, Map<AEKey, BigInteger> inputs) {
        this.inventory = inventory;
        this.inputs = List.of();
        this.exactInputs = Map.copyOf(inputs);
    }

    public static ECOBatchInputLease acquire(ListCraftingInventory inventory, List<GenericStack> inputs) {
        return ECOBatchCraftingHelper.extractExact(inventory, inputs) ? new ECOBatchInputLease(inventory, inputs) : null;
    }

    public static ECOBatchInputLease acquireExact(ECOExactInventory inventory, Map<AEKey, BigInteger> inputs) {
        return inventory.debit(inputs) ? new ECOBatchInputLease(inventory, inputs) : null;
    }

    /** Only a linear batch may refund a proportional, explicitly rejected suffix. */
    public void commitLinear(long accepted, long offered) {
        if (settled) throw new IllegalStateException("Batch input lease already settled");
        if (offered <= 0 || accepted < 0 || accepted > offered || !exactInputs.isEmpty()) {
            throw new IllegalArgumentException("Invalid linear batch settlement");
        }
        var refund = new java.util.ArrayList<GenericStack>();
        for (var input : inputs) {
            if (input.amount() % offered != 0) throw new IllegalArgumentException("Non-linear batch inputs");
            long amount = Math.multiplyExact(input.amount() / offered, offered - accepted);
            if (amount > 0) refund.add(new GenericStack(input.what(), amount));
        }
        settled = true;
        ECOBatchCraftingHelper.insertAll(inventory, refund);
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
            if (exactInputs.isEmpty()) ECOBatchCraftingHelper.insertAll(inventory, inputs);
            else ((ECOExactInventory) inventory).restore(exactInputs);
        }
    }
}
