package cn.dancingsnow.neoecoae.api.me;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Immutable concrete input/output contract for one complete pattern copy, never batch totals. */
public record ECOBatchDispatchContext(IPatternDetails pattern, List<List<GenericStack>> inputs,
        List<GenericStack> outputs, List<GenericStack> containerItems, Level level,
        @Nullable UUID craftingJobId) {
    public ECOBatchDispatchContext {
        Objects.requireNonNull(pattern);
        inputs = inputs.stream().map(List::copyOf).toList();
        outputs = List.copyOf(outputs);
        containerItems = List.copyOf(containerItems);
        counter(inputs.stream().flatMap(List::stream).toList());
        counter(outputs);
        counter(containerItems);
    }

    public static ECOBatchDispatchContext create(IPatternDetails pattern, KeyCounter[] inputs,
            KeyCounter outputs, KeyCounter containers, Level level, @Nullable UUID jobId) {
        return new ECOBatchDispatchContext(pattern,
            java.util.Arrays.stream(inputs).map(ECOFastPathStacks::copyCounter).toList(),
            ECOFastPathStacks.copyCounter(outputs), ECOFastPathStacks.copyCounter(containers), level, jobId);
    }

    public KeyCounter[] inputCounters() {
        return inputs.stream().map(ECOBatchDispatchContext::counter).toArray(KeyCounter[]::new);
    }

    public List<GenericStack> inputItems() {
        return ECOFastPathStacks.copyCounters(inputCounters());
    }

    public ECOExtractedPatternExecution execution() {
        return ECOExtractedPatternExecution.create(pattern, inputCounters(), counter(outputs),
            counter(containerItems), level);
    }

    private static KeyCounter counter(List<GenericStack> stacks) {
        var counter = new KeyCounter();
        for (var stack : stacks) {
            if (stack.amount() <= 0) throw new IllegalArgumentException("Non-positive per-copy amount");
            Math.addExact(counter.get(stack.what()), stack.amount());
            counter.add(stack.what(), stack.amount());
        }
        return counter;
    }
}
