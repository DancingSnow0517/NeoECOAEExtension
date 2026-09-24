package cn.dancingsnow.neoecoae.crafting.display.terminal;

import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Scoped server-thread collector; inactive outside the terminal's ordinary inventory listing call. */
public final class ExactAmountCollector {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private ExactAmountCollector() {}

    public static void begin() { ACTIVE.set(new State()); }

    public static void observe(MEStorage storage, KeyCounter contribution) {
        State state = ACTIVE.get();
        if (state == null) return;
        Map<AEKey, ExactAmount> sourceAmounts = new HashMap<>();
        if (storage instanceof ExactAmountSource source) {
            source.neoecoae$visitExactAmounts(sourceAmounts::put);
        }
        for (var entry : contribution) {
            AEKey key = entry.getKey();
            ExactAmount exact = sourceAmounts.get(key);
            if (exact != null) {
                state.exactKeys.add(key);
                state.totals.merge(key, exact, ExactAmount::add);
            } else state.totals.merge(key, ExactAmount.finite(java.math.BigInteger.valueOf(entry.getLongValue())),
                ExactAmount::add);
        }
    }

    public static Map<AEKey, ExactAmount> finish() {
        State state = ACTIVE.get();
        ACTIVE.remove();
        if (state == null) return Map.of();
        Map<AEKey, ExactAmount> result = new HashMap<>();
        // Individually long-sized disks/hosts can overflow when combined. Their total also
        // needs the side channel, even when no mounted inventory implements ExactAmountSource.
        state.totals.forEach((key, amount) -> {
            if (state.exactKeys.contains(key) || amount.infinite() || amount.value().compareTo(MAX_LONG) > 0) {
                result.put(key, amount);
            }
        });
        return Map.copyOf(result);
    }

    public static void abort() { ACTIVE.remove(); }

    private static final class State {
        private final Map<AEKey, ExactAmount> totals = new HashMap<>();
        private final Set<AEKey> exactKeys = new HashSet<>();
    }
}
