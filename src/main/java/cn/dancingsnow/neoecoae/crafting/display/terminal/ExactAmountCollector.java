package cn.dancingsnow.neoecoae.crafting.display.terminal;

import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.DelegatingMEInventoryAccessor;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

/** Scoped server-thread exact totals, with invalid storage projections sanitized on every listing. */
public final class ExactAmountCollector {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private ExactAmountCollector() {}

    public static void begin() { ACTIVE.set(new State()); }

    public static boolean isActive() { return ACTIVE.get() != null; }

    /** Isolates child-network totals so a storage bus is counted once, after its visibility filter. */
    public static void collect(MEStorage storage, KeyCounter contribution, Consumer<KeyCounter> listing) {
        State parent = ACTIVE.get();
        State nested = parent == null ? null : parent.child();
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
        source.neoecoae$listWithExactAmounts(contribution, state::addExact);
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
        visitAmounts(storage, contribution, nested, state, null);
    }

    /** Reads only listed keys and repairs their legacy projection when an exact source is available. */
    public static void visitAmounts(MEStorage storage, KeyCounter contribution,
            BiConsumer<AEKey, ExactAmount> visitor) {
        visitAmounts(storage, contribution, null, null, visitor);
    }

    private static void visitAmounts(MEStorage storage, KeyCounter contribution, State nested,
            State state, BiConsumer<AEKey, ExactAmount> visitor) {
        // A direct combined source already emitted its authoritative values in this frame.
        Map<AEKey, ExactAmount> sourceAmounts = nested != null && storage instanceof CombinedExactAmountSource
            ? Map.of() : sourceAmounts(storage);
        boolean repaired = false;
        for (var entry : contribution) {
            AEKey key = entry.getKey();
            ExactAmount exact = sourceAmounts.get(key);
            if (exact == null && nested != null) {
                exact = nested.totals.get(key);
            }
            long amount = entry.getLongValue();
            if (exact != null) {
                if (amount < 0) {
                    contribution.set(key, exact.infinite() ? Long.MAX_VALUE : exact.value().min(MAX_LONG).longValueExact());
                    repaired = true;
                }
                if (state != null) state.addExact(key, exact);
                if (visitor != null) visitor.accept(key, exact);
            } else if (amount < 0) {
                // An already-wrapped signed long cannot reveal the real stock. Do not invent
                // an infinite supply or let it cancel another inventory's valid contribution.
                contribution.set(key, 0);
                repaired = true;
            } else if (state != null || visitor != null) {
                // Keep ordinary stock in primitive longs. BigInteger is needed only for an
                // exact source or when several otherwise ordinary inventories overflow.
                long value = nested != null && nested.longs.containsKey(key) ? nested.longs.getLong(key) : amount;
                if (state != null) state.addLong(key, value);
                if (visitor != null) visitor.accept(key, ExactAmount.finite(BigInteger.valueOf(value)));
            }
        }
        if (repaired) contribution.removeZeros();
    }

    static Map<AEKey, ExactAmount> sourceAmounts(MEStorage storage) {
        ExactAmountSource source = findExactSource(storage);
        if (source == null) return Map.of();
        Map<AEKey, ExactAmount> amounts = new HashMap<>();
        source.neoecoae$visitExactAmounts(amounts::put);
        return amounts;
    }

    private static ExactAmountSource findExactSource(MEStorage storage) {
        if (storage instanceof ExactAmountSource source) return source;
        if (!(storage instanceof DelegatingMEInventoryAccessor)) return null;
        ReferenceSet<MEStorage> visited = new ReferenceOpenHashSet<>();
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
        // Individually long-sized disks/hosts can overflow when combined. Their total also
        // needs the side channel, even when no mounted inventory implements ExactAmountSource.
        return state.totals;
    }

    public static void abort() { ACTIVE.remove(); }

    private static final class State {
        private final Object2LongOpenHashMap<AEKey> longs = new Object2LongOpenHashMap<>();
        private final Map<AEKey, ExactAmount> totals = new HashMap<>();
        private State child;

        State child() {
            if (child == null) child = new State();
            child.longs.clear();
            child.totals.clear();
            return child;
        }

        void addLong(AEKey key, long amount) {
            ExactAmount exact = totals.get(key);
            if (exact != null) {
                totals.put(key, exact.add(amount));
                return;
            }
            long previous = longs.getLong(key);
            if (previous > Long.MAX_VALUE - amount) {
                longs.removeLong(key);
                totals.put(key, ExactAmount.finite(BigInteger.valueOf(previous).add(BigInteger.valueOf(amount))));
            } else {
                longs.put(key, previous + amount);
            }
        }

        void addExact(AEKey key, ExactAmount amount) {
            long previous = longs.removeLong(key);
            if (previous != 0) amount = amount.add(previous);
            totals.merge(key, amount, ExactAmount::add);
        }
    }
}
