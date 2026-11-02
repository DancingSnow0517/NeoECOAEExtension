package com.extendedae_plus.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import java.util.List;

/** Minimal EAEP 1.6.x wire shape, used without installing EAEP in the test runtime. */
public class ScaledProcessingPattern implements IPatternDetails {
    private final IPatternDetails original;
    private final cn.dancingsnow.neoecoae.compat.ae2.ECOProcessingExecutionPattern view;
    public ScaledProcessingPattern(IPatternDetails original, long multiplier) {
        this.original = original;
        this.view = new cn.dancingsnow.neoecoae.compat.ae2.ECOProcessingExecutionPattern(original, multiplier);
    }
    public IPatternDetails getOriginal() { return original; }
    public AEItemKey getDefinition() { return original.getDefinition(); }
    public IInput[] getInputs() { return view.getInputs(); }
    public List<GenericStack> getOutputs() { return view.getOutputs(); }
}
