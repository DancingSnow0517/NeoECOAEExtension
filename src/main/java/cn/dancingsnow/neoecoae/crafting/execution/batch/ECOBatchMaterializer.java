package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;

/** Converts one pure plan into one disposable physical dispatch attempt. */
public final class ECOBatchMaterializer {
    public ECOBatchMaterialized materialize(ECOBatchPlan plan, KeyCounter[] perCraftInputs,
            KeyCounter perCraftOutputs, KeyCounter perCraftRemainders, ListCraftingInventory inventory, Level level) {
        long count = plan.craftCount();
        KeyCounter[] inputs = scaleCounters(perCraftInputs, count);
        KeyCounter outputs = scaleCounter(perCraftOutputs, count);
        KeyCounter remainders = scaleCounter(perCraftRemainders, count);
        List<GenericStack> physicalInputs = flatten(inputs);
        ECOBatchInputLease lease = ECOBatchInputLease.acquire(inventory, physicalInputs);
        if (lease == null) return null;
        try {
            var view = new ECOLinearBatchExecutionView(plan.identity().originalPattern(), count,
                    perCraftInputs, perCraftOutputs, perCraftRemainders, level);
            return new ECOBatchMaterialized(plan.identity(), count, inputs, outputs, remainders, lease, view);
        } catch (RuntimeException failure) {
            lease.rollback();
            throw failure;
        }
    }

    private static KeyCounter[] scaleCounters(KeyCounter[] counters, long count) {
        KeyCounter[] result = new KeyCounter[counters.length];
        for (int i = 0; i < counters.length; i++) result[i] = scaleCounter(counters[i], count);
        return result;
    }

    private static KeyCounter scaleCounter(KeyCounter counter, long count) {
        KeyCounter result = new KeyCounter();
        for (var entry : counter) result.set(entry.getKey(), Math.multiplyExact(entry.getLongValue(), count));
        return result;
    }

    private static List<GenericStack> flatten(KeyCounter[] counters) {
        List<GenericStack> result = new ArrayList<>();
        for (KeyCounter counter : counters) result.addAll(ECOFastPathStacks.copyCounter(counter));
        return List.copyOf(result);
    }
}
