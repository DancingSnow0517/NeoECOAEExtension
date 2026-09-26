package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.PatternInputSink;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.Level;

/** Linear execution view; it never implements or pretends to be an AE2 pattern. */
public final class ECOLinearBatchExecutionView implements ECOBatchExecutionView {
    private final IPatternDetails originalPattern;
    private final long craftCount;
    private final List<List<GenericStack>> inputs;
    private final List<GenericStack> outputs;
    private final List<GenericStack> remainders;
    private final Level level;

    public ECOLinearBatchExecutionView(IPatternDetails originalPattern, long craftCount, KeyCounter[] inputCounters,
            KeyCounter outputCounter, KeyCounter remainderCounter, Level level) {
        this.originalPattern = Objects.requireNonNull(originalPattern, "originalPattern");
        this.craftCount = craftCount;
        this.level = Objects.requireNonNull(level, "level");
        this.inputs = Arrays.stream(inputCounters)
                .map(counter -> ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(counter), craftCount))
                .toList();
        this.outputs = ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(outputCounter), craftCount);
        this.remainders = ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(remainderCounter), craftCount);
    }

    @Override public IPatternDetails originalPattern() { return originalPattern; }
    @Override public long craftCount() { return craftCount; }
    @Override public List<List<GenericStack>> scaledInputs() { return inputs; }
    @Override public List<GenericStack> scaledOutputs() { return outputs; }
    @Override public List<GenericStack> scaledRemainders() { return remainders; }
    @Override public boolean supportsExternalInputPush() { return originalPattern.supportsPushInputsToExternalInventory(); }
    @Override public void pushInputs(KeyCounter[] counters, PatternInputSink sink) {
        originalPattern.pushInputsToExternalInventory(counters, sink);
    }
    @Override public Level level() { return level; }
}
