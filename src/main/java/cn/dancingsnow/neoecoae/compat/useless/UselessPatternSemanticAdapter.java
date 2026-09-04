package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import org.jetbrains.annotations.Nullable;

/**
 * Static-planning contract for Useless Mod's dynamic-component and omniversal patterns.
 *
 * <p>Useless deliberately permits selected input slots to match by item id or tag. The encoded primary stack is
 * still a valid concrete choice, so ECO may commit the plan to it and record substitution semantics. Dynamic output
 * slots are different: their runtime data components are selected by the machine/CPU integration and cannot be
 * proven equal to the encoded output key, so exact cycle algebra must decline them.
 */
public final class UselessPatternSemanticAdapter implements PatternSemanticAdapter {
    private final AE2PatternSemanticAdapter delegate = new AE2PatternSemanticAdapter();

    @Override
    public boolean supports(IPatternDetails pattern) {
        return dynamicView(pattern) != null;
    }

    @Override
    public PatternSemantics analyze(IPatternDetails pattern) {
        UselessDynamicPatternView dynamic = dynamicView(pattern);
        if (dynamic == null) {
            return PatternSemantics.unsupported(pattern, safeDefinition(pattern), "USELESS_DYNAMIC_CONTRACT_MISSING");
        }
        PatternSemantics base = delegate.analyze(pattern);
        if (!base.supported()) return base;
        try {
            if (dynamic.neoecoae$usesDynamicOutputs()) {
                return PatternSemantics.unsupported(pattern, base.physicalDefinition(),
                    "USELESS_DYNAMIC_OUTPUT_NOT_STATIC");
            }
            boolean relaxedInput = false;
            IPatternDetails.IInput[] inputs = pattern.getInputs();
            for (int slot = 0; slot < inputs.length; slot++) {
                if (dynamic.neoecoae$isItemIdInput(slot) || dynamic.neoecoae$isTagInput(slot)
                        || dynamic.neoecoae$isFluidTagInput(slot)) {
                    relaxedInput = true;
                    break;
                }
            }
            return new PatternSemantics(pattern, base.physicalDefinition(), base.consumedInputs(),
                base.producedOutputs(), base.returnedOutputs(), base.feedbackEdges(),
                relaxedInput ? PatternSemantics.MatchingMode.SUBSTITUTION : base.matchingMode(),
                PatternSemantics.ExecutionRestriction.NONE, true, base.cycleSafe(), null);
        } catch (RuntimeException rejected) {
            return PatternSemantics.unsupported(pattern, base.physicalDefinition(),
                "USELESS_SEMANTIC_ANALYSIS_FAILED:" + rejected.getClass().getSimpleName());
        }
    }

    @Override
    public String name() {
        return "UselessMod";
    }

    @Nullable
    private static UselessDynamicPatternView dynamicView(IPatternDetails pattern) {
        if (pattern == null) return null;
        IPatternDetails candidate = pattern;
        for (int depth = 0; depth < 4; depth++) {
            if (candidate instanceof UselessDynamicPatternView dynamic) return dynamic;
            if (!(candidate instanceof UselessScaledPatternView scaled)) return null;
            IPatternDetails next = scaled.neoecoae$getOriginal();
            if (next == null || next == candidate) return null;
            candidate = next;
        }
        return null;
    }

    @Nullable
    private static Object safeDefinition(IPatternDetails pattern) {
        try {
            return pattern.getDefinition();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

}
