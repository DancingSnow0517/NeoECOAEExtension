package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Identity-scoped NET_GROWTH_SAFE evidence issued by the smart pattern bus. */
public final class NetGrowthPatternValidationRegistry {
    private static final Set<IPatternDetails> VALIDATED =
        Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private NetGrowthPatternValidationRegistry() {}

    /** Called by the smart pattern bus immediately before it publishes a decoded pattern. */
    public static boolean validateAndRegisterFromSmartPatternBus(IPatternDetails details) {
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
            Map<appeng.api.stacks.AEKey, Long> consumed = new LinkedHashMap<>();
            Map<appeng.api.stacks.AEKey, Long> produced = new LinkedHashMap<>();
            Map<appeng.api.stacks.AEKey, Long> remainder = new LinkedHashMap<>();
            var outputs = details.getOutputs();
            for (var output : outputs) {
                produced.merge(output.what(), output.amount(), Math::addExact);
            }
            for (var input : details.getInputs()) {
                var possible = input.getPossibleInputs();
                var key = possible[0].what();
                consumed.merge(key, Math.multiplyExact(possible[0].amount(), input.getMultiplier()), Math::addExact);
                var remaining = input.getRemainingKey(key);
                if (remaining != null) remainder.merge(remaining, input.getMultiplier(), Math::addExact);
            }
            int feedbackKeys = 0;
            for (var entry : consumed.entrySet()) {
                long returned = Math.addExact(produced.getOrDefault(entry.getKey(), 0L),
                    remainder.getOrDefault(entry.getKey(), 0L));
                if (returned > 0L) {
                    feedbackKeys++;
                    if (returned <= entry.getValue()) return false;
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
                Math.multiplyExact(possible[0].amount(), input.getMultiplier());
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
}
