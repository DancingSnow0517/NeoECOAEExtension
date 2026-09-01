package cn.dancingsnow.neoecoae.impl.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Normalizes the public AE2 pattern contract without depending on a concrete pattern implementation. */
public final class AE2PatternSemanticAdapter implements PatternSemanticAdapter {
    private final Predicate<AEKey> reusableStockKeyVerifier;

    public AE2PatternSemanticAdapter() {
        this(key -> key instanceof AEItemKey item && !item.toStack(1).isDamageableItem());
    }

    /** Injectable verifier for isolated planner tests and integrations with an equivalent immutable-key proof. */
    public AE2PatternSemanticAdapter(Predicate<AEKey> reusableStockKeyVerifier) {
        this.reusableStockKeyVerifier = Objects.requireNonNull(reusableStockKeyVerifier);
    }

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
            boolean hasRemainder = false;
            boolean everyRemainderIsExactReusableStock = true;
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                if (input == null) return PatternSemantics.unsupported(pattern, definition, "NULL_INPUT");
                GenericStack[] possible = input.getPossibleInputs();
                if (possible == null || possible.length == 0 || possible[0] == null
                        || possible[0].what() == null) {
                    return PatternSemantics.unsupported(pattern, definition, "INVALID_INPUT");
                }
                long multiplier = input.getMultiplier();
                if (multiplier <= 0L) {
                    return PatternSemantics.unsupported(pattern, definition, "INVALID_INPUT_AMOUNT");
                }

                // AE2 lists the primary ingredient first, followed by substitutes. For a reusable-stock slot,
                // commit to an exact, non-damageable 1:1 alternative when one exists (for example Mystical
                // Agriculture's master infusion crystal). This is deliberately a proof, not an item-id allow-list:
                // ordinary infusion crystals are damageable and therefore cannot satisfy it.
                InputChoice choice = chooseInput(input, possible, multiplier);
                GenericStack selected = choice.stack();
                AEKey remaining = choice.remaining();
                PlannerAmount amount = PlannerAmount.of(selected.amount()).multiply(multiplier);
                PlannerAmount remainingAmount = remaining == null ? PlannerAmount.ZERO : PlannerAmount.of(multiplier);
                inputs.add(new PatternSemantics.Input(input, selected.what(), amount, remaining, remainingAmount));
                if (possible.length != 1) matching = PatternSemantics.MatchingMode.SUBSTITUTION;
                if (remaining != null) {
                    hasRemainder = true;
                    everyRemainderIsExactReusableStock &= choice.exactReusableStock();
                    returned.add(new GenericStack(remaining, multiplier));
                    if (!outputs.isEmpty() && outputs.getFirst() != null && outputs.getFirst().what() != null) {
                        feedback.add(new PatternSemantics.FeedbackEdge(remaining, outputs.getFirst().what(),
                            remainingAmount));
                    }
                }
            }
            return new PatternSemantics(pattern, definition, inputs, outputs, returned, feedback, matching,
                PatternSemantics.ExecutionRestriction.NONE, true,
                hasRemainder && everyRemainderIsExactReusableStock, null);
        } catch (RuntimeException rejected) {
            return PatternSemantics.unsupported(pattern, definition, "MALFORMED_PATTERN:" + rejected.getClass().getSimpleName());
        }
    }

    private InputChoice chooseInput(IPatternDetails.IInput input, GenericStack[] possible, long multiplier) {
        InputChoice primary = inspectChoice(input, possible[0], multiplier);
        if (primary.exactReusableStock()) return primary;
        for (int i = 1; i < possible.length; i++) {
            InputChoice alternative = inspectChoice(input, possible[i], multiplier);
            if (alternative.exactReusableStock()) return alternative;
        }
        return primary;
    }

    private InputChoice inspectChoice(IPatternDetails.IInput input, GenericStack stack, long multiplier) {
        if (stack == null || stack.what() == null || stack.amount() <= 0L) {
            throw new IllegalArgumentException("Invalid possible pattern input");
        }
        AEKey remaining = input.getRemainingKey(stack.what());
        boolean exactReusableStock = multiplier > 0L
            && stack.amount() == 1L
            && remaining != null
            && remaining.equals(stack.what())
            && reusableStockKeyVerifier.test(stack.what());
        return new InputChoice(stack, remaining, exactReusableStock);
    }

    private record InputChoice(GenericStack stack, AEKey remaining, boolean exactReusableStock) {}
}
