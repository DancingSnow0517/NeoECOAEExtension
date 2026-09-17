package cn.dancingsnow.neoecoae.terminal.bigamount;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Collects exact amounts during AE2's ordinary listing, respecting nested inventory contributions. */
public final class ExactAmountCollector {
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private ExactAmountCollector() {}

    public record Listing(KeyCounter stacks, Map<AEKey, BigInteger> amounts) {}

    public static Listing collect(MEStorage storage, Supplier<KeyCounter> listing) {
        State previous = ACTIVE.get();
        State state = new State();
        ACTIVE.set(state);
        try {
            KeyCounter stacks = listing.get();
            Map<AEKey, BigInteger> exact = resolve(storage, stacks, state);
            exact.entrySet().removeIf(entry -> entry.getValue().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0);
            return new Listing(stacks, Map.copyOf(exact));
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    /** Isolating each nested call prevents counting both a network and its children. */
    public static void contribution(MEStorage storage, KeyCounter output, Runnable listing) {
        State parent = ACTIVE.get();
        if (parent == null) {
            listing.run();
            return;
        }
        State child = new State(parent.domains);
        ACTIVE.set(child);
        try {
            listing.run();
            Map<AEKey, BigInteger> exact = resolve(storage, output, child);
            if (storage instanceof ExactAmountSource source
                    && !exact.isEmpty()
                    && !parent.domains.add(source.neoecoae$exactInventoryIdentity())) {
                for (var entry : output) output.set(entry.getKey(), 0);
                return;
            }
            for (var entry : output) {
                if (entry.getLongValue() <= 0) continue;
                AEKey key = entry.getKey();
                BigInteger amount = exact.get(key);
                if (amount != null) parent.exactKeys.add(key);
                parent.totals.merge(
                        key, amount != null ? amount : BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
            }
        } finally {
            ACTIVE.set(parent);
        }
    }

    private static Map<AEKey, BigInteger> resolve(MEStorage storage, KeyCounter stacks, State state) {
        Map<AEKey, BigInteger> result = new HashMap<>();
        for (var entry : stacks) {
            if (entry.getLongValue() <= 0) continue;
            AEKey key = entry.getKey();
            if (storage instanceof ExactAmountSource source) {
                BigInteger amount = source.neoecoae$getExactAmount(key);
                if (amount.signum() > 0) result.put(key, amount);
            } else if (state.exactKeys.contains(key)) {
                result.put(key, state.totals.get(key));
            }
        }
        return result;
    }

    private static final class State {
        private final Map<AEKey, BigInteger> totals = new HashMap<>();
        private final Set<AEKey> exactKeys = new HashSet<>();
        private final Set<Object> domains;

        private State() {
            this(Collections.newSetFromMap(new IdentityHashMap<>()));
        }

        private State(Set<Object> domains) {
            this.domains = domains;
        }
    }
}
