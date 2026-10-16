package cn.dancingsnow.neoecoae.crafting.planner.growth;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemanticAdapters;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Identity-scoped NET_GROWTH_SAFE evidence issued by the smart pattern bus. */
public final class NetGrowthPatternValidationRegistry {
    private static final WeakIdentityGenerationMap<IPatternDetails> VALIDATED = new WeakIdentityGenerationMap<>();

    private NetGrowthPatternValidationRegistry() {}

    /** Called by the smart pattern bus immediately before it publishes a decoded pattern. */
    public static boolean validateAndRegisterFromSmartPatternBus(IPatternDetails details) {
        return validateAndRegister(details);
    }

    /** Called by the opt-in cycle planner when a pattern was published while cycle planning was disabled. */
    public static boolean validateAndRegisterFromPlanner(IPatternDetails details) {
        return validateAndRegister(details);
    }

    private static boolean validateAndRegister(IPatternDetails details) {
        long generation = AE2PatternIntrospection.reloadGeneration();
        if (deterministicStaticContract(details) == null) return false;
        if (generation != AE2PatternIntrospection.reloadGeneration()) return false;
        VALIDATED.add(details, generation);
        // A clear racing this add can leave only an old-generation marker, never a usable authorization.
        return generation == AE2PatternIntrospection.reloadGeneration();
    }

    /** Client-safe tooltip classification using the same deterministic contract accepted by the bus. */
    public static boolean isSelfGrowingPattern(IPatternDetails details) {
        long generation = AE2PatternIntrospection.reloadGeneration();
        PatternSemantics semantics = deterministicStaticContract(details);
        if (semantics == null) return false;
        try {
            Map<appeng.api.stacks.AEKey, PlannerAmount> consumed = new LinkedHashMap<>();
            Map<appeng.api.stacks.AEKey, PlannerAmount> produced = new LinkedHashMap<>();
            Map<appeng.api.stacks.AEKey, PlannerAmount> remainder = new LinkedHashMap<>();
            for (var output : semantics.producedOutputs()) {
                produced.merge(output.what(), PlannerAmount.of(output.amount()), PlannerAmount::add);
            }
            for (var input : semantics.consumedInputs()) {
                consumed.merge(input.key(), input.amountPerPattern(), PlannerAmount::add);
            }
            for (var returned : semantics.returnedOutputs()) {
                remainder.merge(returned.what(), PlannerAmount.of(returned.amount()), PlannerAmount::add);
            }
            int feedbackKeys = 0;
            for (var entry : consumed.entrySet()) {
                PlannerAmount returned = produced.getOrDefault(entry.getKey(), PlannerAmount.ZERO)
                    .add(remainder.getOrDefault(entry.getKey(), PlannerAmount.ZERO));
                if (returned.signum() > 0) {
                    feedbackKeys++;
                    if (returned.compareTo(entry.getValue()) <= 0) return false;
                }
            }
            return feedbackKeys == 1 && generation == AE2PatternIntrospection.reloadGeneration();
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    private static PatternSemantics deterministicStaticContract(IPatternDetails details) {
        if (details == null) return null;
        try {
            PatternSemantics semantics = PatternSemanticAdapters.analyze(details);
            if (!semantics.completeForStaticPlanning() || !semantics.cycleSafeForStaticPlanning()
                    || semantics.consumedInputs().isEmpty() || semantics.producedOutputs().isEmpty()) return null;
            var outputs = details.getOutputs();
            var inputs = details.getInputs();
            if (outputs == null || outputs.isEmpty() || inputs == null || inputs.length == 0) return null;
            for (var output : outputs) {
                if (output == null || output.what() == null || output.amount() <= 0) return null;
            }
            for (var input : inputs) {
                if (input == null || input.getMultiplier() <= 0) return null;
                var possible = input.getPossibleInputs();
                if (possible == null || possible.length != 1 || possible[0] == null
                        || possible[0].what() == null || possible[0].amount() <= 0) return null;
                PlannerAmount.of(possible[0].amount()).multiply(input.getMultiplier());
                if (possible[0].what() instanceof AEItemKey item && item.toStack(1).isDamageableItem()) return null;
                var remaining = input.getRemainingKey(possible[0].what());
                if (remaining instanceof AEItemKey item && item.toStack(1).isDamageableItem()) return null;
            }
            // Registered special semantics must preserve the same finite positive stock contract too.
            for (var input : semantics.consumedInputs()) {
                if (input == null || input.key() == null || input.amountPerPattern().signum() <= 0) return null;
                if (input.key() instanceof AEItemKey item && item.toStack(1).isDamageableItem()) return null;
            }
            for (var output : semantics.producedOutputs()) {
                if (output == null || output.what() == null || output.amount() <= 0) return null;
            }
            for (var returned : semantics.returnedOutputs()) {
                if (returned == null || returned.what() == null || returned.amount() <= 0) return null;
                if (returned.what() instanceof AEItemKey item && item.toStack(1).isDamageableItem()) return null;
            }
            return semantics;
        } catch (RuntimeException rejected) {
            return null;
        }
    }

    /** Read-only planner lookup; identity is intentional because the bus publishes this exact instance. */
    public static boolean isValidated(IPatternDetails details) {
        if (details == null) return false;
        long generation = AE2PatternIntrospection.reloadGeneration();
        return VALIDATED.contains(details, generation)
            && generation == AE2PatternIntrospection.reloadGeneration();
    }

    public static void clear() {
        VALIDATED.clear();
    }

    private static final class WeakIdentityGenerationMap<T> {
        private final ReferenceQueue<T> queue = new ReferenceQueue<>();
        private final Map<IdentityRef<T>, Long> entries = new HashMap<>();

        synchronized void add(T value, long generation) {
            expunge();
            entries.put(new IdentityRef<>(value, queue), generation);
        }

        synchronized boolean contains(T value, long generation) {
            expunge();
            Long validatedGeneration = entries.get(new IdentityRef<>(value));
            return validatedGeneration != null && validatedGeneration == generation;
        }

        synchronized void clear() {
            entries.clear();
            while (queue.poll() != null) {
                // Drain references that were enqueued before the clear.
            }
        }

        private void expunge() {
            Reference<? extends T> reference;
            while ((reference = queue.poll()) != null) {
                entries.remove(reference);
            }
        }
    }

    private static final class IdentityRef<T> extends WeakReference<T> {
        private final int identityHash;

        IdentityRef(T value, ReferenceQueue<T> queue) {
            super(value, queue);
            identityHash = System.identityHashCode(value);
        }

        IdentityRef(T value) {
            super(value);
            identityHash = System.identityHashCode(value);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IdentityRef<?> reference)) {
                return false;
            }
            Object value = get();
            return value != null && value == reference.get();
        }
    }
}
