package cn.dancingsnow.neoecoae.impl.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.ArrayList;
import java.util.List;

/** Normalizes the public AE2 pattern contract without depending on a concrete pattern implementation. */
public final class AE2PatternSemanticAdapter implements PatternSemanticAdapter {
    @Override
    public boolean supports(IPatternDetails pattern) {
        return pattern != null;
    }

    @Override
    public PatternSemantics analyze(IPatternDetails pattern) {
        AEItemKey definition = null;
        try {
            definition = pattern.getDefinition();
            List<GenericStack> outputs = pattern.getOutputs() == null ? List.of() : List.copyOf(pattern.getOutputs());
            List<PatternSemantics.Input> inputs = new ArrayList<>();
            List<GenericStack> returned = new ArrayList<>();
            List<PatternSemantics.FeedbackEdge> feedback = new ArrayList<>();
            PatternSemantics.MatchingMode matching = PatternSemantics.MatchingMode.EXACT;
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                if (input == null) return PatternSemantics.unsupported(pattern, definition, "NULL_INPUT");
                GenericStack[] possible = input.getPossibleInputs();
                if (possible == null || possible.length == 0 || possible[0] == null
                        || possible[0].what() == null) {
                    return PatternSemantics.unsupported(pattern, definition, "INVALID_INPUT");
                }
                GenericStack primary = possible[0];
                long multiplier = input.getMultiplier();
                if (primary.amount() <= 0L || multiplier <= 0L) {
                    return PatternSemantics.unsupported(pattern, definition, "INVALID_INPUT_AMOUNT");
                }
                AEKey remaining = input.getRemainingKey(primary.what());
                PlannerAmount amount = PlannerAmount.of(primary.amount()).multiply(multiplier);
                PlannerAmount remainingAmount = remaining == null ? PlannerAmount.ZERO : PlannerAmount.of(multiplier);
                inputs.add(new PatternSemantics.Input(input, primary.what(), amount, remaining, remainingAmount));
                if (possible.length != 1) matching = PatternSemantics.MatchingMode.SUBSTITUTION;
                if (remaining != null) {
                    returned.add(new GenericStack(remaining, multiplier));
                    if (!outputs.isEmpty() && outputs.getFirst() != null && outputs.getFirst().what() != null) {
                        feedback.add(new PatternSemantics.FeedbackEdge(remaining, outputs.getFirst().what(),
                            remainingAmount));
                    }
                }
            }
            return new PatternSemantics(pattern, definition, inputs, outputs, returned, feedback, matching,
                PatternSemantics.ExecutionRestriction.NONE, true, false, null);
        } catch (RuntimeException rejected) {
            return PatternSemantics.unsupported(pattern, definition, "MALFORMED_PATTERN:" + rejected.getClass().getSimpleName());
        }
    }
}
