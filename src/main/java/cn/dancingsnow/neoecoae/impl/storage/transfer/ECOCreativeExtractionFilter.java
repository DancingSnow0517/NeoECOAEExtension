package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;

import java.math.BigInteger;
import java.util.function.LongSupplier;

/** Filters actual source inventories, so a creative disk cannot hide a finite disk's debit. */
public final class ECOCreativeExtractionFilter {
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private ECOCreativeExtractionFilter() {}

    public static long extract(MEStorage storage, AEKey key, long amount, IActionSource source) {
        State previous = ACTIVE.get();
        ACTIVE.set(new State());
        try {
            return extractSource(storage, key, Actionable.MODULATE,
                () -> storage.extract(key, amount, Actionable.MODULATE, source));
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    /** Called at each NetworkStorage mount, only while a filtered interface input is active. */
    public static long extractSource(MEStorage storage, AEKey key, Actionable mode, LongSupplier extraction) {
        State state = ACTIVE.get();
        if (state == null || mode != Actionable.MODULATE) return extraction.getAsLong();
        long checkedBefore = state.checkedSources;
        ExactAmount before = storedAmount(storage, key);
        long extracted = extraction.getAsLong();
        // Nested networks already filtered their physical sources. Never reclassify their aggregate.
        if (state.checkedSources != checkedBefore) return extracted;
        state.checkedSources++;
        if (extracted > 0 && (before.infinite() || before.value().signum() > 0)
                && before.equals(storedAmount(storage, key))) return 0L;
        return extracted;
    }

    private static ExactAmount storedAmount(MEStorage storage, AEKey key) {
        if (storage instanceof ExactAmountSource source) {
            ExactAmount[] exact = {null};
            source.neoecoae$visitExactAmounts((what, amount) -> {
                if (key.equals(what)) exact[0] = amount;
            });
            if (exact[0] != null) return exact[0];
        }
        KeyCounter stacks = new KeyCounter();
        storage.getAvailableStacks(stacks);
        return ExactAmount.finite(BigInteger.valueOf(Math.max(0L, stacks.get(key))));
    }

    private static final class State {
        long checkedSources;
    }
}
