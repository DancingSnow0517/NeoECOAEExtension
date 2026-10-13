package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import java.math.BigInteger;
import java.util.Map;

/** Accumulates the planned outputs of a job's tasks into a caller-provided sink. */
final class ExecutionOutputs {
    private ExecutionOutputs() {}

    /** Adds {@code output.amount() * crafts} for every output, saturating at {@link Long#MAX_VALUE}. */
    static void addSaturating(Map<IPatternDetails, ExecutingCraftingJob.TaskProgress> tasks, KeyCounter sink) {
        for (var task : tasks.entrySet()) {
            long crafts = task.getValue().value;
            for (var output : task.getKey().getOutputs()) {
                sink.set(output.what(), NEMath.saturatingAdd(sink.get(output.what()),
                        NEMath.saturatingMultiply(output.amount(), crafts)));
            }
        }
    }

    /** Exact counterpart of {@link #addSaturating}: adds {@code output.amount() * remainingExact} without bounds. */
    static void addExact(Map<IPatternDetails, ExecutingCraftingJob.TaskProgress> tasks, Map<AEKey, BigInteger> sink) {
        for (var task : tasks.entrySet()) {
            BigInteger crafts = task.getValue().remainingExact();
            for (var output : task.getKey().getOutputs()) {
                sink.merge(output.what(), crafts.multiply(BigInteger.valueOf(output.amount())), BigInteger::add);
            }
        }
    }
}
