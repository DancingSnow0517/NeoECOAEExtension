package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Identity-scoped NET_GROWTH_SAFE evidence issued by the smart pattern bus. */
public final class NetGrowthPatternValidationRegistry {
    private static final WeakIdentitySet<IPatternDetails> VALIDATED = new WeakIdentitySet<>();

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
        if (details == null || !AE2PatternIntrospection.isKnownSafePatternType(details)) return false;
        if (!hasDeterministicStaticContract(details)) return false;
        VALIDATED.add(details);
        return true;
    }

    /** Client-safe tooltip classification using the same deterministic contract accepted by the bus. */
    public static boolean isSelfGrowingPattern(IPatternDetails details) {
        if (details == null || !AE2PatternIntrospection.isKnownSafePatternType(details)
                || !hasDeterministicStaticContract(details)) return false;
        try {
            Map<appeng.api.stacks.AEKey, PlannerAmount> consumed = new LinkedHashMap<>();
            Map<appeng.api.stacks.AEKey, PlannerAmount> produced = new LinkedHashMap<>();
            Map<appeng.api.stacks.AEKey, PlannerAmount> remainder = new LinkedHashMap<>();
            var outputs = details.getOutputs();
            for (var output : outputs) {
                produced.merge(output.what(), PlannerAmount.of(output.amount()), PlannerAmount::add);
            }
            for (var input : details.getInputs()) {
                var possible = input.getPossibleInputs();
                var key = possible[0].what();
                consumed.merge(key, PlannerAmount.of(possible[0].amount()).multiply(input.getMultiplier()),
                    PlannerAmount::add);
                var remaining = input.getRemainingKey(key);
                if (remaining != null) remainder.merge(remaining, PlannerAmount.of(input.getMultiplier()),
                    PlannerAmount::add);
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
            return feedbackKeys == 1;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    private static boolean hasDeterministicStaticContract(IPatternDetails details) {
        try {
            var outputs = details.getOutputs();
            var inputs = details.getInputs();
            if (outputs == null || outputs.isEmpty() || inputs == null || inputs.length == 0) return false;
            for (var output : outputs) {
                if (output == null || output.what() == null || output.amount() <= 0) return false;
            }
            for (var input : inputs) {
                if (input == null || input.getMultiplier() <= 0) return false;
                var possible = input.getPossibleInputs();
                if (possible == null || possible.length != 1 || possible[0] == null
                        || possible[0].what() == null || possible[0].amount() <= 0) return false;
                PlannerAmount.of(possible[0].amount()).multiply(input.getMultiplier());
                if (possible[0].what() instanceof AEItemKey item && item.toStack(1).isDamageableItem()) return false;
                input.getRemainingKey(possible[0].what());
            }
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    /** Read-only planner lookup; identity is intentional because the bus publishes this exact instance. */
    public static boolean isValidated(IPatternDetails details) {
        return details != null && VALIDATED.contains(details);
    }

    public static void clear() {
        VALIDATED.clear();
    }

    private static final class WeakIdentitySet<T> {
        private final ReferenceQueue<T> queue = new ReferenceQueue<>();
        private final Set<IdentityRef<T>> entries = new HashSet<>();

        synchronized void add(T value) {
            expunge();
            entries.add(new IdentityRef<>(value, queue));
        }

        synchronized boolean contains(T value) {
            expunge();
            return entries.contains(new IdentityRef<>(value));
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
