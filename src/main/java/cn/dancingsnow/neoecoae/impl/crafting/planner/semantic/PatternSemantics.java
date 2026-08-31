package cn.dancingsnow.neoecoae.impl.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Normalized static semantics consumed by the graph compiler.  It intentionally keeps contract facts separate from
 * the raw {@link IPatternDetails} implementation so SCC detection does not need integration-specific instanceof
 * checks.
 */
public record PatternSemantics(
    IPatternDetails physicalPattern,
    @Nullable Object physicalDefinition,
    List<Input> consumedInputs,
    List<GenericStack> producedOutputs,
    List<GenericStack> returnedOutputs,
    List<FeedbackEdge> feedbackEdges,
    MatchingMode matchingMode,
    ExecutionRestriction executionRestriction,
    boolean exactStaticAnalysis,
    boolean cycleSafe,
    @Nullable String unsupportedReason
) {
    public PatternSemantics {
        Objects.requireNonNull(physicalPattern, "physicalPattern");
        consumedInputs = List.copyOf(consumedInputs);
        producedOutputs = List.copyOf(producedOutputs);
        returnedOutputs = List.copyOf(returnedOutputs);
        feedbackEdges = List.copyOf(feedbackEdges);
        Objects.requireNonNull(matchingMode, "matchingMode");
        Objects.requireNonNull(executionRestriction, "executionRestriction");
    }

    public boolean supported() {
        return exactStaticAnalysis && unsupportedReason == null;
    }

    /** Unknown matching/restriction semantics can never be approximated into a SUCCESS result. */
    public boolean completeForStaticPlanning() {
        return supported() && matchingMode == MatchingMode.EXACT
            && executionRestriction == ExecutionRestriction.NONE;
    }

    public boolean cycleSafeForStaticPlanning() {
        return completeForStaticPlanning() && cycleSafe;
    }

    public Set<AEKey> consumedKeys() {
        Set<AEKey> keys = new LinkedHashSet<>();
        for (Input input : consumedInputs) if (input.key() != null) keys.add(input.key());
        return Set.copyOf(keys);
    }

    public Set<AEKey> producedKeys() {
        Set<AEKey> keys = new LinkedHashSet<>();
        for (GenericStack output : producedOutputs) if (output != null && output.what() != null) keys.add(output.what());
        return Set.copyOf(keys);
    }

    public Set<AEKey> returnedKeys() {
        Set<AEKey> keys = new LinkedHashSet<>();
        for (GenericStack output : returnedOutputs) if (output != null && output.what() != null) keys.add(output.what());
        return Set.copyOf(keys);
    }

    public static PatternSemantics unsupported(IPatternDetails pattern, @Nullable Object definition,
            String reason) {
        return new PatternSemantics(pattern, definition, List.of(), List.of(), List.of(), List.of(),
            MatchingMode.UNKNOWN, ExecutionRestriction.UNKNOWN, false, false, reason);
    }

    public record Input(IPatternDetails.IInput source, AEKey key, PlannerAmount amountPerPattern,
            @Nullable AEKey returnedKey, PlannerAmount returnedAmountPerPattern) {
        public Input {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(amountPerPattern, "amountPerPattern");
            Objects.requireNonNull(returnedAmountPerPattern, "returnedAmountPerPattern");
        }
    }

    /** A returned/reusable key creates a dependency from that key back to the dependent produced key. */
    public record FeedbackEdge(AEKey returnedKey, AEKey dependentOutput, PlannerAmount amountPerFiring) {
        public FeedbackEdge {
            Objects.requireNonNull(returnedKey, "returnedKey");
            Objects.requireNonNull(dependentOutput, "dependentOutput");
            Objects.requireNonNull(amountPerFiring, "amountPerFiring");
        }
    }

    public enum MatchingMode {
        EXACT,
        SUBSTITUTION,
        FUZZY,
        UNKNOWN
    }

    public enum ExecutionRestriction {
        NONE,
        PROVIDER_LOOKUP,
        CPU,
        FIRING_EXPANSION,
        UNKNOWN
    }
}
