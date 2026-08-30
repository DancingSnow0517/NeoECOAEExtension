package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Identity-scoped NET_GROWTH_SAFE evidence issued by the smart pattern bus. */
public final class NetGrowthPatternValidationRegistry {
    private static final Set<IPatternDetails> VALIDATED =
        Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private NetGrowthPatternValidationRegistry() {}

    /** Called by the smart pattern bus immediately before it publishes a decoded pattern. */
    public static boolean validateAndRegisterFromSmartPatternBus(IPatternDetails details) {
        if (details == null || !AE2PatternIntrospection.isKnownSafePatternType(details)) return false;
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
                if (possible[0].what() instanceof AEItemKey item
                        && item.toStack(1).isDamageableItem()) return false;
                // Force the bus validation boundary to resolve the exact remainder contract now.
                input.getRemainingKey(possible[0].what());
            }
            VALIDATED.add(details);
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
