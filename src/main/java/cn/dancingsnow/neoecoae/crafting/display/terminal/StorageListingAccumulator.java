package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.Consumer;

/** Reuses one mounted-inventory counter per network; ordinary listings need only one merge pass. */
public final class StorageListingAccumulator {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private final KeyCounter contribution = new KeyCounter();

    public void append(MEStorage storage, KeyCounter output, Consumer<KeyCounter> listing) {
        if (!ExactAmountCollector.isActive() && storage instanceof ExactAmountSource source
                && source.neoecoae$hasSaturatingListing()) {
            listing.accept(output);
            return;
        }
        contribution.clear();
        // KeyCounter.addAll can clone a source submap efficiently when the destination
        // has no submap. Keeping an empty variant map instead forces entry-by-entry
        // addition, which allocates more for inventories with many NBT variants.
        contribution.removeEmptySubmaps();
        if (ExactAmountCollector.isActive()) {
            ExactAmountCollector.collect(storage, contribution, listing);
            SaturatingStackAccumulator.addAll(output, contribution);
            return;
        }

        listing.accept(contribution);
        Map<AEKey, ExactAmount> exactAmounts = null;
        for (var entry : contribution) {
            long amount = entry.getLongValue();
            if (amount < 0) {
                // Malformed third-party projections are rare. Query exact values only on
                // this cold path, so valid ordinary listings never traverse their source twice.
                if (exactAmounts == null) exactAmounts = ExactAmountCollector.sourceAmounts(storage);
                ExactAmount exact = exactAmounts.get(entry.getKey());
                if (exact == null) continue;
                amount = exact.infinite() ? Long.MAX_VALUE : exact.value().min(MAX_LONG).longValueExact();
            }
            if (amount != 0) SaturatingStackAccumulator.add(output, entry.getKey(), amount);
        }
    }
}
