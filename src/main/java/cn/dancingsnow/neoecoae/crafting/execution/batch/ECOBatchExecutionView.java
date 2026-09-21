package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.PatternInputSink;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import net.minecraft.world.level.Level;

/** Ephemeral execution description for exactly one materialized batch. */
public interface ECOBatchExecutionView {
    IPatternDetails originalPattern();

    long craftCount();

    List<List<appeng.api.stacks.GenericStack>> scaledInputs();

    List<appeng.api.stacks.GenericStack> scaledOutputs();

    List<appeng.api.stacks.GenericStack> scaledRemainders();

    boolean supportsExternalInputPush();

    void pushInputs(KeyCounter[] counters, PatternInputSink sink);

    Level level();
}
