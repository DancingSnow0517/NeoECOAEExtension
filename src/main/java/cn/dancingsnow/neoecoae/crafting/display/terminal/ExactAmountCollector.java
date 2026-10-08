package cn.dancingsnow.neoecoae.crafting.display.terminal;

import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.DelegatingMEInventoryAccessor;

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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Scoped server-thread exact totals, with invalid storage projections sanitized on every listing. */
public final class ExactAmountCollector {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private ExactAmountCollector() {}

    public static void begin() { ACTIVE.set(new State()); }

    /** Isolates child-network totals so a storage bus is counted once, after its visibility filter. */
    public static void collect(MEStorage storage, KeyCounter contribution, Consumer<KeyCounter> listing) {
        State parent = ACTIVE.get();
        State nested = parent == null ? null : new State();
        if (nested != null) ACTIVE.set(nested);
        try {
            if (!collectCombined(storage, contribution)) listing.accept(contribution);
        } finally {
            if (nested != null) ACTIVE.set(parent);
        }
        observe(storage, contribution, nested);
    }

    /** Returns false outside terminal collection or for sources that need the ordinary listing callback. */
    public static boolean collectCombined(MEStorage storage, KeyCounter contribution) {
        State state = ACTIVE.get();
        if (state == null || !(storage instanceof CombinedExactAmountSource source)) return false;
        source.neoecoae$listWithExactAmounts(contribution, (key, exact) -> {
            state.exactKeys.add(key);
            state.totals.merge(key, exact, ExactAmount::add);
        });
        return true;
    }

    public static void observe(MEStorage storage, KeyCounter contribution) {
        observe(storage, contribution, null);
    }

    private static void observe(MEStorage storage, KeyCounter contribution, State nested) {
        State state = ACTIVE.get();
        // Listings between side-channel updates still must not expose negative stock to AE2.
        if (state == null) {
            boolean negative = false;
            for (var entry : contribution) negative |= entry.getLongValue() < 0;
            if (!negative) return;
        }
        visitAmounts(storage, contribution, nested, (key, exact, known) -> {
            if (state != null) {
                if (known) state.exactKeys.add(key);
                state.totals.merge(key, exact, ExactAmount::add);
            }
        });
    }

    /** Reads only listed keys and repairs their legacy projection when an exact source is available. */
    public static void visitAmounts(MEStorage storage, KeyCounter contribution,
            BiConsumer<AEKey, ExactAmount> visitor) {
        visitAmounts(storage, contribution, null, (key, amount, known) -> visitor.accept(key, amount));
    }

    private static void visitAmounts(MEStorage storage, KeyCounter contribution, State nested,
            AmountVisitor visitor) {
        Map<AEKey, ExactAmount> sourceAmounts = Map.of();
        ExactAmountSource source = findExactSource(storage);
        // A direct combined source already emitted its authoritative values in this frame.
        if (source != null && !(nested != null && storage instanceof CombinedExactAmountSource)) {
            sourceAmounts = new HashMap<>();
            source.neoecoae$visitExactAmounts(sourceAmounts::put);
        }
        boolean repaired = false;
        for (var entry : contribution) {
            AEKey key = entry.getKey();
            ExactAmount exact = sourceAmounts.get(key);
            boolean known = exact != null;
            if (exact == null && nested != null) {
                exact = nested.totals.get(key);
                known = nested.exactKeys.contains(key);
            }
            long amount = entry.getLongValue();
            if (exact != null) {
                if (amount < 0) {
                    contribution.set(key, exact.infinite() ? Long.MAX_VALUE : exact.value().min(MAX_LONG).longValueExact());
                    repaired = true;
                }
            } else if (amount < 0) {
                // An already-wrapped signed long cannot reveal the real stock. Do not invent
                // an infinite supply or let it cancel another inventory's valid contribution.
                contribution.set(key, 0);
                repaired = true;
                continue;
            } else {
                exact = ExactAmount.finite(BigInteger.valueOf(amount));
            }
            visitor.accept(key, exact, known);
        }
        if (repaired) contribution.removeZeros();
    }

    private static ExactAmountSource findExactSource(MEStorage storage) {
        if (storage instanceof ExactAmountSource source) return source;
        if (!(storage instanceof DelegatingMEInventoryAccessor)) return null;
        Set<MEStorage> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (storage != null && visited.add(storage)) {
            if (storage instanceof ExactAmountSource source) return source;
            if (!(storage instanceof DelegatingMEInventoryAccessor wrapper)) break;
            storage = wrapper.neoecoae$getDelegate();
        }
        return null;
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
        return result;
    }

    public static void abort() { ACTIVE.remove(); }

    @FunctionalInterface
    private interface AmountVisitor {
        void accept(AEKey key, ExactAmount amount, boolean known);
    }

    private static final class State {
        private final Map<AEKey, ExactAmount> totals = new HashMap<>();
        private final Set<AEKey> exactKeys = new HashSet<>();
    }
}
