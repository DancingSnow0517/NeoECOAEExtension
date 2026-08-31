package cn.dancingsnow.neoecoae.impl.crafting.planner.identity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * The single plan/pattern identity implementation used by planner metadata and runtime phase rebinding.
 *
 * <p>A final output is only one field of a plan.  In particular, it is not an identity for a candidate produced by
 * a different planner.  This class therefore always carries the complete executable task vector together with the
 * inventory vectors that affect the submitted job.</p>
 */
public final class PlanIdentity {
    private PlanIdentity() {
    }

    public static @Nullable Signature of(@Nullable ICraftingPlan plan) {
        if (plan == null || plan.finalOutput() == null || plan.finalOutput().what() == null) return null;
        Map<PatternIdentity, Long> patternTimes = taskSignature(plan.patternTimes());
        if (patternTimes == null) return null;
        return new Signature(plan.finalOutput().what(), plan.finalOutput().amount(), patternTimes,
            counterContents(plan.usedItems()), counterContents(plan.emittedItems()), counterContents(plan.missingItems()));
    }

    /** Strict full-plan equality. No output-only or production-scaled fallback is allowed here. */
    public static boolean matches(@Nullable Signature expected, @Nullable ICraftingPlan actual) {
        return expected != null && expected.equals(of(actual));
    }

    public static boolean matches(@Nullable ICraftingPlan left, @Nullable ICraftingPlan right) {
        Signature leftSignature = of(left);
        return leftSignature != null && leftSignature.equals(of(right));
    }

    public static boolean samePattern(@Nullable IPatternDetails left, @Nullable IPatternDetails right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        PatternIdentity leftIdentity = patternIdentity(left);
        PatternIdentity rightIdentity = patternIdentity(right);
        return leftIdentity != null && leftIdentity.equals(rightIdentity);
    }

    /** Stable structural identity exposed to execution-plan validation. */
    public static @Nullable PatternIdentity patternIdentityFor(@Nullable IPatternDetails pattern) {
        return patternIdentity(pattern);
    }

    /** Exact task-vector comparison that still permits AE2 to reconstruct wrapper instances. */
    public static boolean sameTaskCounts(@Nullable Map<IPatternDetails, Long> left,
            @Nullable Map<IPatternDetails, Long> right) {
        Map<PatternIdentity, Long> leftSignature = taskSignature(left);
        Map<PatternIdentity, Long> rightSignature = taskSignature(right);
        return leftSignature != null && leftSignature.equals(rightSignature);
    }

    public static Map<PatternIdentity, Long> taskSignature(@Nullable Map<IPatternDetails, Long> tasks) {
        if (tasks == null) return null;
        Map<PatternIdentity, Long> result = new LinkedHashMap<>();
        for (var entry : tasks.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0L) return null;
            PatternIdentity identity = patternIdentity(entry.getKey());
            if (identity == null) return null;
            Long existing = result.get(identity);
            if (existing != null && Long.MAX_VALUE - existing < entry.getValue()) return null;
            result.put(identity, existing == null ? entry.getValue() : existing + entry.getValue());
        }
        return Map.copyOf(result);
    }

    public static long executionCount(@Nullable Map<IPatternDetails, Long> tasks) {
        if (tasks == null) return 0L;
        long total = 0L;
        for (Long value : tasks.values()) {
            if (value == null || value <= 0L) continue;
            if (Long.MAX_VALUE - total < value) return Long.MAX_VALUE;
            total += value;
        }
        return total;
    }

    public static String describe(@Nullable Signature signature) {
        if (signature == null) return "<unavailable>";
        return "output=" + signature.finalWhat() + "x" + signature.finalAmount()
            + ",patternKinds=" + signature.patternTimes().size()
            + ",taskExecutions=" + signature.executionCount()
            + ",usedItems=" + signature.usedItems().size()
            + ",emittedItems=" + signature.emittedItems().size()
            + ",signatureHash=" + Integer.toHexString(signature.hashCode());
    }

    private static Map<AEKey, Long> counterContents(@Nullable KeyCounter counter) {
        if (counter == null) return Map.of();
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var value : counter) result.put(value.getKey(), value.getLongValue());
        return Map.copyOf(result);
    }

    private static @Nullable PatternIdentity patternIdentity(IPatternDetails pattern) {
        try {
            Object definition = pattern.getDefinition();
            if (definition != null) return new PatternIdentity(Kind.DEFINITION, definition);

            List<PatternInputIdentity> inputs = new ArrayList<>();
            IPatternDetails.IInput[] rawInputs = pattern.getInputs();
            if (rawInputs == null) return objectIdentity(pattern);
            for (IPatternDetails.IInput input : rawInputs) {
                if (input == null) return objectIdentity(pattern);
                GenericStack[] possible = input.getPossibleInputs();
                if (possible == null || possible.length == 0) return objectIdentity(pattern);
                List<StackIdentity> alternatives = new ArrayList<>();
                Map<AEKey, AEKey> remaining = new LinkedHashMap<>();
                for (GenericStack candidate : possible) {
                    if (candidate == null || candidate.what() == null) return objectIdentity(pattern);
                    alternatives.add(new StackIdentity(candidate.what(), candidate.amount()));
                    remaining.put(candidate.what(), input.getRemainingKey(candidate.what()));
                }
                inputs.add(new PatternInputIdentity(List.copyOf(alternatives), input.getMultiplier(), remaining));
            }

            List<StackIdentity> outputs = new ArrayList<>();
            List<GenericStack> rawOutputs = pattern.getOutputs();
            if (rawOutputs == null) return objectIdentity(pattern);
            for (GenericStack output : rawOutputs) {
                if (output == null || output.what() == null) return objectIdentity(pattern);
                outputs.add(new StackIdentity(output.what(), output.amount()));
            }
            return new PatternIdentity(Kind.STRUCTURAL,
                new StructuralIdentity(pattern.getClass().getName(), List.copyOf(inputs), List.copyOf(outputs)));
        } catch (RuntimeException rejected) {
            return objectIdentity(pattern);
        }
    }

    private static PatternIdentity objectIdentity(IPatternDetails pattern) {
        return new PatternIdentity(Kind.OBJECT, new ObjectIdentity(pattern));
    }

    public enum Kind {
        DEFINITION,
        STRUCTURAL,
        OBJECT
    }

    /** Stable pattern identity when AE2 supplies an encoded definition. */
    public record PatternIdentity(Kind kind, Object value) {
        public PatternIdentity {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(value, "value");
        }
    }

    public record Signature(AEKey finalWhat, long finalAmount, Map<PatternIdentity, Long> patternTimes,
            Map<AEKey, Long> usedItems, Map<AEKey, Long> emittedItems, Map<AEKey, Long> missingItems) {
        public Signature {
            Objects.requireNonNull(finalWhat, "finalWhat");
            patternTimes = Map.copyOf(patternTimes);
            usedItems = Map.copyOf(usedItems);
            emittedItems = Map.copyOf(emittedItems);
            missingItems = Map.copyOf(missingItems);
        }

        public long executionCount() {
            long total = 0L;
            for (long value : patternTimes.values()) {
                if (value <= 0L) continue;
                if (Long.MAX_VALUE - total < value) return Long.MAX_VALUE;
                total += value;
            }
            return total;
        }

        public boolean sameFinalOutput(Signature other) {
            return other != null && finalAmount == other.finalAmount && finalWhat.equals(other.finalWhat);
        }
    }

    private record StackIdentity(AEKey what, long amount) {
    }

    private record PatternInputIdentity(List<StackIdentity> alternatives, long multiplier,
            Map<AEKey, AEKey> remaining) {
        private PatternInputIdentity {
            alternatives = List.copyOf(alternatives);
            // A null remainder is meaningful in the public AE2 contract; keep it while freezing the map.
            remaining = Collections.unmodifiableMap(new LinkedHashMap<>(remaining));
        }
    }

    private record StructuralIdentity(String implementationClass, List<PatternInputIdentity> inputs,
            List<StackIdentity> outputs) {
        private StructuralIdentity {
            Objects.requireNonNull(implementationClass, "implementationClass");
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }
    }

    private static final class ObjectIdentity {
        private final IPatternDetails value;

        private ObjectIdentity(IPatternDetails value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ObjectIdentity identity && identity.value == value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }

        @Override
        public String toString() {
            return value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
        }
    }
}
