package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.List;

public record CompiledPattern(
    int id,
    IPatternDetails details,
    AEKey producedKey,
    long outputPerPattern,
    List<CompiledInput> inputs,
    List<GenericStack> outputs,
    boolean fastSupported,
    String unsupportedReason
) {
    public CompiledPattern {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }
}
