package cn.dancingsnow.neoecoae.api.me.provider;

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
public final class ECOBatchDispatchContext {
    private final IPatternDetails pattern;
    private final List<List<GenericStack>> inputs;
    private final List<GenericStack> outputs;
    private final List<GenericStack> containerItems;
    private final Level level;
    @Nullable
    private final UUID craftingJobId;
    private final List<GenericStack> inputItems;
    private final KeyCounter outputCounter;
    private final KeyCounter containerCounter;
    @Nullable
    private ECOExtractedPatternExecution execution;

    public ECOBatchDispatchContext(IPatternDetails pattern, List<List<GenericStack>> inputs,
            List<GenericStack> outputs, List<GenericStack> containerItems, Level level,
            @Nullable UUID craftingJobId) {
        this.pattern = Objects.requireNonNull(pattern);
        this.inputs = inputs.stream().map(List::copyOf).toList();
        this.outputs = List.copyOf(outputs);
        this.containerItems = List.copyOf(containerItems);
        this.level = level;
        this.craftingJobId = craftingJobId;
        var totalInputs = new KeyCounter();
        for (var slot : this.inputs) {
            addChecked(totalInputs, slot);
        }
        this.inputItems = ECOFastPathStacks.copyCounter(totalInputs);
        // Retain checked counters for execution instead of discarding validation work.
        this.outputCounter = counter(this.outputs);
        this.containerCounter = counter(this.containerItems);
    }

    public IPatternDetails pattern() { return pattern; }
    public List<List<GenericStack>> inputs() { return inputs; }
    public List<GenericStack> outputs() { return outputs; }
    public List<GenericStack> containerItems() { return containerItems; }
    public Level level() { return level; }
    public @Nullable UUID craftingJobId() { return craftingJobId; }

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
        return inputItems;
    }

    public ECOExtractedPatternExecution execution() {
        // Capacity probing and commit share one synchronous server-thread dispatch.
        var result = execution;
        if (result == null) {
            result = ECOExtractedPatternExecution.create(pattern, inputCounters(), outputCounter,
                containerCounter, level);
            execution = result;
        }
        return result;
    }

    private static KeyCounter counter(List<GenericStack> stacks) {
        var counter = new KeyCounter();
        addChecked(counter, stacks);
        return counter;
    }

    private static void addChecked(KeyCounter counter, List<GenericStack> stacks) {
        for (var stack : stacks) {
            if (stack.amount() <= 0) throw new IllegalArgumentException("Non-positive per-copy amount");
            Math.addExact(counter.get(stack.what()), stack.amount());
            counter.add(stack.what(), stack.amount());
        }
    }
}
